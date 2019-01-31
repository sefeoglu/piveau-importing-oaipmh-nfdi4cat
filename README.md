# piveau importing oai-pmh
Importing records via the OAI-PMH protocol and feeding a pipe.

## Pipe config parameters

_mandatory_

`address` Address of the source

_optional_

`incremental` Requires 

`lastRun` set in pipe header

`filters` A map of key value pairs to filter datasets

## Data info for payload

`total` Total number of datasets

`counter` The number of this dataset

`identifier` The unique identifier in the source of this dataset
