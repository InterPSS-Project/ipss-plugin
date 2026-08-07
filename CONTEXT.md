# Sensitivity Domain Glossary

## Study

A portable, reusable container that references a network, shared endpoint definitions, calculation options, and one or more sensitivity specifications. A study is not limited to one sensitivity type.

## Sensitivity specification

One independently defined sensitivity workload within a study, such as PTDF, Shift Factor, LODF, or multi-outage LODF. Specifications share the study's endpoint catalog and calculation options and execute in their listed order.

## Analysis type

The kind of calculation represented by a sensitivity specification. It labels result rows; it does not select the only calculation allowed in a study.

## Single-analysis study

A study containing exactly one sensitivity specification. It is the same domain concept as a multi-analysis study, with a more concise creation form for callers that need only one calculation.
