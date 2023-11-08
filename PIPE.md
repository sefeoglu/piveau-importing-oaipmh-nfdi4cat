# Pipe Segment Configuration

## _mandatory_

* `catalogue`

  The id of the target catalogue

### Either:

* `address`

  Address of the source

### Or in case of using an adapter:

* `resource`

* `metadata`

## _optional_

* `queries`

  Map of query parameters to use in request.

* `outputFormat`

  Mimetype to use for payload. Default is `application/n-triples`

  Possible output formats are:

    * `application/rdf+xml`
    * `application/n-triples`
    * `application/ld+json`
    * `application/trig`
    * `text/turtle`
    * `text/n3`

* `sendListDelay`

  The delay in milliseconds before the list of identifiers is send. Take precedence over service configuration (see `PVEAU_IMPORTING_SEND_LIST_DELAY`)

# Data Info Object

* `total`

  Total number of datasets

* `counter`

  The number of this dataset

* `identifier`

  The unique identifier in the source of this dataset

* `catalogue`

  The id of the target catalogue

* `hash`

  The hash value calculated at the source
