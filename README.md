# piveau importing oai-pmh
Microservice for importing from source and feeding a piveau pipe.

## Pipe config parameters

_mandatory_

`address` Address of the source

_optional_

`pageSize` Default value is `100`

`incremental` Requires 

`lastRun` set in pipe header

`filters` A map of key value pairs to filter datasets

## Data info for payload

`total` Total number of datasets

`counter` The number of this dataset

`identifier` The unique identifier in the source of this dataset
