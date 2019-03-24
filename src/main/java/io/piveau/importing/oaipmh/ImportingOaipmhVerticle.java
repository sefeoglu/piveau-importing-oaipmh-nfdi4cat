package io.piveau.importing.oaipmh;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.piveau.importing.oaipmh.responses.OAIPMHResponse;
import io.piveau.importing.oaipmh.responses.OAIPMHResult;
import io.piveau.pipe.connector.PipeContext;
import io.piveau.utils.Hash;
import io.piveau.utils.JenaUtils;
import io.vertx.circuitbreaker.CircuitBreaker;
import io.vertx.circuitbreaker.CircuitBreakerOptions;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.jdom2.*;
import org.jdom2.filter.Filters;
import org.jdom2.input.SAXBuilder;
import org.jdom2.output.Format;
import org.jdom2.output.XMLOutputter;
import org.jdom2.xpath.XPathExpression;
import org.jdom2.xpath.XPathFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.StringReader;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public class ImportingOaipmhVerticle extends AbstractVerticle {

    private static final Namespace oaiNamespace = Namespace.getNamespace("oai", "http://www.openarchives.org/OAI/2.0/");

    private static final String XML_PATH_OAIPMH_RECORD_IDENTIFIER = "/oai:record/oai:header/oai:identifier/text()";
    private static final String XML_PATH_OAIPMH_RECORD_METADATA = "/oai:record/oai:metadata/*[1]";

    public static final String ADDRESS = "io.piveau.pipe.importing.oaipmh.queue";

    private WebClient client;

    private CircuitBreaker breaker;

    @Override
    public void start(Future<Void> startFuture) {
        vertx.eventBus().consumer(ADDRESS, this::handlePipe);
        client = WebClient.create(vertx);

        breaker = CircuitBreaker.create("oaipmh-breaker", vertx, new CircuitBreakerOptions().setMaxRetries(2).setTimeout(15000))
                .retryPolicy(count -> count * 10000L);

        startFuture.complete();
    }

    private void handlePipe(Message<PipeContext> message) {
        PipeContext pipeContext = message.body();
        JsonNode config = pipeContext.getConfig();
        String mode = config.path("mode").asText("metadata");
        pipeContext.log().info("Import started. Mode '{}'", mode);

        if ("identifiers".equals(mode)) {
            fetchIdentifiers(null, pipeContext, new HashSet<>());
        } else {
            fetch(null, pipeContext, new ArrayList<>());
        }
    }

    private void fetch(String resumptionToken, PipeContext pipeContext, List<String> identifiers) {

        JsonNode config = pipeContext.getConfig();
        String address = config.path("address").textValue();

        HttpRequest<Buffer> request = client.getAbs(address)
                .addQueryParam("verb", "ListRecords");
//                .expect(ResponsePredicate.SC_SUCCESS);

        if (resumptionToken != null) {
            request.addQueryParam("resumptionToken", resumptionToken);
        }

        breaker.<HttpResponse<Buffer>>execute(fut -> request.send(ar -> {
            if (ar.succeeded()) {
                HttpResponse<Buffer> response = ar.result();
                if (response.statusCode() == 200) {
                    fut.complete(ar.result());
                } else {
                    pipeContext.log().warn("{} - retrying...", response.statusMessage());
                    fut.fail(response.statusMessage() + " - " + response.bodyAsString());
                }
            } else {
                pipeContext.log().error("Sent metadata request", ar.cause());
                fut.fail(ar.cause());
            }
        })).setHandler(ar -> {
            if (ar.succeeded()) {
                HttpResponse<Buffer> response = ar.result();
                ByteArrayInputStream stream = new ByteArrayInputStream(response.bodyAsString().getBytes());
                try {
                    Document document = new SAXBuilder().build(stream);

                    OAIPMHResponse oaipmhResponse = new OAIPMHResponse(document);
                    if (oaipmhResponse.isSuccess()) {
                        String outputFormat = config.path("outputFormat").asText("application/n-triples");

                        XPathFactory xpFactory = XPathFactory.instance();
                        XPathExpression<Text> identifierExpression = xpFactory.compile(XML_PATH_OAIPMH_RECORD_IDENTIFIER, Filters.text(), Collections.emptyMap(), oaiNamespace);
                        XPathExpression<Element> metadataExpression = xpFactory.compile(XML_PATH_OAIPMH_RECORD_METADATA, Filters.element(), Collections.emptyMap(), oaiNamespace);

                        OAIPMHResult result = oaipmhResponse.getResult();
                        List<Document> records = result.getRecords();
                        records.forEach(doc -> {

                            Text identifier = identifierExpression.evaluateFirst(doc);
                            Element dataset = metadataExpression.evaluateFirst(doc);

                            String output = new XMLOutputter(Format.getPrettyFormat()).outputString(dataset);

                            identifiers.add(identifier.getTextTrim());
                            ObjectNode dataInfo = new ObjectMapper().createObjectNode()
                                    .put("total", result.completeSize())
                                    .put("counter", identifiers.size())
                                    .put("identifier", identifier.getTextTrim())
                                    .put("hash", Hash.asHexString(output));

                            output = "<?xml version=\"1.0\" encoding=\"utf-8\" ?>\n" +
                                    "<rdf:RDF xmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\" >\n" +
                                    output +
                                    "\n</rdf:RDF>";

                            try {
                                Model m = JenaUtils.read(output.getBytes(), "application/rdf+xml");
                                String normalized = JenaUtils.write(m, outputFormat);
                                pipeContext.setResult(normalized, outputFormat, dataInfo).forward(client);
                                pipeContext.log().info("Data imported: {}", dataInfo.toString());
                            } catch (Exception e) {
                                pipeContext.log().error("Normalize model", e);
                            }
                        });
                        if (result.token() != null && !result.token().isEmpty()) {
                            fetch(result.token(), pipeContext, identifiers);
                        } else {
                            pipeContext.log().info("Import metadata finished");
                            vertx.setTimer(5000, t -> {
                                pipeContext.setResult(new JsonArray(identifiers).encodePrettily(), "application/json", new ObjectMapper().createObjectNode().put("content", "identifierList")).forward(client);
                            });
                        }
                    } else {

                        pipeContext.setFailure(oaipmhResponse.getError().getMessage());
                    }
                } catch (Exception e) {
                    pipeContext.setFailure(e);
                }
            } else {
                pipeContext.setFailure(ar.cause());
            }
        });

    }

    private void fetchIdentifiers(String token, PipeContext pipeContext, Set<String> identifiers) {
        JsonNode config = pipeContext.getConfig();
        String address = config.path("address").textValue();

        HttpRequest<Buffer> request = client.getAbs(address).addQueryParam("verb", "ListIdentifiers");

        if (token != null) {
            request.addQueryParam("resumptionToken", token);
        }

        breaker.<HttpResponse<Buffer>>execute(fut -> request.send(ar -> {
            if (ar.succeeded()) {
                HttpResponse<Buffer> response = ar.result();
                if (response.statusCode() == 200) {
                    fut.complete(ar.result());
                } else {
                    pipeContext.log().error("Fetch identifiers: {} - {}", response.statusMessage(), response.bodyAsString());
                    fut.fail(response.statusMessage());
                }
            } else {
                pipeContext.log().error("Sent identifiers request", ar.cause());
                fut.fail(ar.cause());
            }
        })).setHandler(ar -> {
            if (ar.succeeded()) {
                HttpResponse<Buffer> response = ar.result();
                ByteArrayInputStream stream = new ByteArrayInputStream(response.bodyAsString().getBytes());
                try {
                    Document document = new SAXBuilder().build(stream);
                    OAIPMHResponse oaipmhResponse = new OAIPMHResponse(document);
                    if (oaipmhResponse.isError()) {
                        pipeContext.setFailure(oaipmhResponse.getError().getMessage());
                    } else {
                        OAIPMHResult result = oaipmhResponse.getResult();
                        identifiers.addAll(result.getIdentifiers());
                        pipeContext.log().debug("Fetched {} identifiers so far", identifiers.size());
                        String nextToken = result.token();
                        if (nextToken != null && !nextToken.isEmpty()) {
                            fetchIdentifiers(nextToken, pipeContext, identifiers);
                        } else {
                            pipeContext.log().info("Fetching identifiers finished: {} identifiers", identifiers.size());
                            pipeContext.setResult(new JsonArray(new ArrayList<>(identifiers)).encodePrettily(), "application/json").forward(client);
                        }
                    }
                } catch (Exception e) {
                    pipeContext.setFailure(e);
                }
            } else {
                pipeContext.setFailure(ar.cause());
            }
        });
    }

}
