FROM docker.elastic.co/elasticsearch/elasticsearch:8.18.3

# Install Nori plugin
RUN elasticsearch-plugin install --batch analysis-nori

# Copy decimal-unit-merge plugin directly to plugins directory
COPY --chown=elasticsearch:elasticsearch plugins/decimal-unit-merge /usr/share/elasticsearch/plugins/decimal-unit-merge