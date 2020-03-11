#!/usr/bin/env bash

cd data || exit 1
curl https://www.mvcr.cz/soubor/metodicky-material-k-problematice-peceteni-zsvd.aspx --output sample.signed.pdf

cd .. || exit 1
mvn clean compile

rm results-tmp.log || true
rm results.log || true

for i in {1..10}
do
   mvn exec:java -Dexec.args="data/sample.signed.pdf"
   cat results-tmp.log >> results.log
done
