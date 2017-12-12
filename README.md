# Xml parser plugin for Embulk
[![Gem Version](https://badge.fury.io/rb/embulk-parser-xpath2.svg)](https://badge.fury.io/rb/embulk-parser-xpath2)
[![Build Status](https://travis-ci.org/maji-KY/embulk-parser-xpath2.svg?branch=develop)](https://travis-ci.org/maji-KY/embulk-parser-xpath2)
[![Codacy Badge](https://api.codacy.com/project/badge/Grade/1af04aa85e2b477e945c93158512d3b2)](https://www.codacy.com/app/maji-KY/embulk-parser-xpath2?utm_source=github.com&amp;utm_medium=referral&amp;utm_content=maji-KY/embulk-parser-xpath2&amp;utm_campaign=Badge_Grade)
[![CodeFactor](https://www.codefactor.io/repository/github/maji-ky/embulk-parser-xpath2/badge)](https://www.codefactor.io/repository/github/maji-ky/embulk-parser-xpath2)
[![Known Vulnerabilities](https://snyk.io/test/github/maji-ky/embulk-parser-xpath2/badge.svg)](https://snyk.io/test/github/maji-ky/embulk-parser-xpath2)

Embulk parser plugin for parsing xml data by XPath perfectly!

## Features

- namespace awareness
- nullable columns

## Overview

* **Plugin type**: parser
* **Guess supported**: no

## Configuration

- **type**: specify this plugin as `"xpath2"` (string, required)
- **root**: root element to start fetching each entries (string, required)
- **schema**: specify the attribute of table and data type (required)
- **namespaces**: specify namespaces (required)
- **stop_on_invalid_record**: stop bulk load transaction if a invalid record is found (boolean, default is `false`)

## Example

```yaml
parser:
  type: xpath2
  root: '/ns1:root/ns2:entry'
  schema:
    - { path: 'ns2:id', name: id, type: long }
    - { path: 'ns2:title', name: title, type: string }
    - { path: 'ns2:meta/ns2:author', name: author, type: string }
    - { path: 'ns2:date', name: date, type: timestamp, format: '%Y%m%d' }
    - { path: 'ns2:ratings/ns2:rating[@by="subscribers"]', name: ratings, type: json }
  namespaces: {ns1: 'http://example.com/ns1/', ns2: 'http://example.com/ns2/'}
```

Then you can fetch entries from the following xml:
```xml
<?xml version="1.0"?>
<ns1:root
  xmlns:ns1="http://example.com/ns1/"
  xmlns:ns2="http://example.com/ns2/">
  <ns2:entry>
    <ns2:id>1</ns2:id>
    <ns2:title>Hello!</ns2:title>
    <ns2:meta>
      <ns2:author>maji-KY</ns2:author>
    </ns2:meta>
    <ns2:date>20010101</ns2:date>
    <ns2:ratings>
      <ns2:rating by="subscribers">1</ns2:rating>
      <ns2:rating by="subscribers">2</ns2:rating>
      <ns2:rating>3</ns2:rating>
    </ns2:ratings>
  </ns2:entry>
</ns1:root>
```
## Build

```
$ ./gradlew gem
```

## Benchmark

```
$ sbt benchmark/jmh:run
```
