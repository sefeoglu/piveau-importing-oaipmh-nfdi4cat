package io.piveau.importing.oaipmh;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.piveau.importing.oaipmh.responses.OAIPMHResponse;
import io.piveau.importing.oaipmh.responses.OAIPMHResult;
import io.piveau.pipe.connector.PipeContext;
import io.vertx.circuitbreaker.CircuitBreaker;
import io.vertx.circuitbreaker.CircuitBreakerOptions;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.Message;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.predicate.ResponsePredicate;
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
import java.io.IOException;
import java.io.StringReader;
import java.util.Collections;
import java.util.List;
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

        breaker = CircuitBreaker.create("oaipmh-breaker", vertx, new CircuitBreakerOptions().setMaxRetries(2))
                .retryPolicy(count -> count * 10000L);

        startFuture.complete();
    }

    private void handlePipe(Message<PipeContext> message) {
        PipeContext pipeContext = message.body();

        pipeContext.log().info("Import started");

        fetch(null, pipeContext, new AtomicInteger());
    }

    private void fetch(String resumptionToken, PipeContext pipeContext, AtomicInteger counter) {

        JsonNode config = pipeContext.getConfig();
        String address = config.path("address").textValue();

        HttpRequest<Buffer> request = client.getAbs(address)
                .addQueryParam("verb", "ListRecords")
                .expect(ResponsePredicate.SC_SUCCESS);

        if (resumptionToken != null) {
            request.addQueryParam("resumptionToken", resumptionToken);
        }

        breaker.<HttpResponse<Buffer>>execute(fut -> request.send(fut.completer()))
                .setHandler(ar -> {
                    if (ar.succeeded()) {
                        HttpResponse<Buffer> response = ar.result();
                        ByteArrayInputStream stream = new ByteArrayInputStream(response.bodyAsString().getBytes());
                        try {
                            Document document = new SAXBuilder().build(stream);

                            OAIPMHResponse oaipmhResponse = new OAIPMHResponse(document);
                            if (oaipmhResponse.isSuccess()) {
                                OAIPMHResult result = oaipmhResponse.getResult();
                                List<Document> records = result.getRecords();
                                records.forEach(doc -> {

                                    XPathFactory xpFactory = XPathFactory.instance();
                                    XPathExpression<Text> identifierExpression = xpFactory.compile(XML_PATH_OAIPMH_RECORD_IDENTIFIER, Filters.text(), Collections.emptyMap(), oaiNamespace);
                                    Text identifier = identifierExpression.evaluateFirst(doc);
                                    XPathExpression<Element> metadataExpression = xpFactory.compile(XML_PATH_OAIPMH_RECORD_METADATA, Filters.element(), Collections.emptyMap(), oaiNamespace);
                                    Element dataset = metadataExpression.evaluateFirst(doc);

                                    ObjectNode dataInfo = new ObjectMapper().createObjectNode()
                                            .put("total", result.completeSize())
                                            .put("counter", counter.incrementAndGet())
                                            .put("identifier", identifier.getTextTrim());

                                    String output = new XMLOutputter(Format.getPrettyFormat()).outputString(dataset);
                                    pipeContext.log().trace(output);

                                    output = "<?xml version=\"1.0\" encoding=\"utf-8\" ?>\n" +
                                            "<rdf:RDF xmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\" >\n" +
                                            output +
                                            "\n</rdf:RDF>";

                                    String outputFormat = config.get("outputFormat").textValue();

                                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                                    try {
                                        Model m = ModelFactory.createDefaultModel();
                                        m.read(new StringReader(output), "RDF/XML");
                                        m.write(out, outputFormat);
                                    } catch (Exception e) {
                                        pipeContext.log().error("normalize model", e);
                                        return;
                                    }
                                    pipeContext.setResult(out.toString(), outputFormat, dataInfo).forward(vertx);
                                    pipeContext.log().info("Data imported: " + dataInfo.toString());

                                });
                                if (result.token() != null && !result.token().isEmpty()) {
                                    fetch(result.token(), pipeContext, counter);
                                } else {
                                    pipeContext.log().info("Import finished");
                                }
                            } else {
                                pipeContext.setFailure(oaipmhResponse.getError().getMessage());
                            }
                        } catch (JDOMException | IOException e) {
                            pipeContext.setFailure(e.getMessage());
                        }
                    } else {
                        pipeContext.setFailure(ar.cause().getMessage());
                    }
                });
    }

}
