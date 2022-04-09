FROM samyaks/object-analyzer-analyzer

COPY --from=samyaks/object-analyzer-ant-tracks-analyzer /anttracks/Tool/CLI/build/libs/CLI.jar CLI.jar

COPY --from=samyaks/object-analyzer-ant-tracks-jvm /anttracks/jdk8u/dist/slowdebug-64/j2sdk-image jvm

ENV PATH="/workdir/jvm/bin:${PATH}"

CMD object-analyzer
