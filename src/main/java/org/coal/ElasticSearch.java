package org.coal;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

import java.io.UnsupportedEncodingException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ElasticSearch {
    private static final Configuration configuration = Configuration.getInstance();
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private static HttpPut setUpRequest() throws JsonProcessingException, UnsupportedEncodingException {
        String esHost = configuration.getElasticConnectionString();
        String pipelineName = "log-parser-pipeline";

        Map<String, Object> pipeline = new HashMap<>();
        pipeline.put("description", "Parse log files with grok pattern");

        List<Map<String, Object>> processors = new ArrayList<>();

        // Grok processor
        Map<String, Object> grokProcessor = new HashMap<>();
        Map<String, Object> grok = new HashMap<>();
        grok.put("field", "message");
        grok.put("patterns", List.of(
                "\\[%{TIMESTAMP_ISO8601:timestamp}\\] \\(%{TIME}\\)\\(%{DATA:component}\\)\\(%{DATA:source_file}\\)\\(\\s*%{INT:line_number:int}\\): %{GREEDYDATA:log_message}"
        ));
        grok.put("ignore_failure", true);
        grokProcessor.put("grok", grok);
        processors.add(grokProcessor);

        // Date processor
        Map<String, Object> dateProcessor = new HashMap<>();
        Map<String, Object> date = new HashMap<>();
        date.put("field", "timestamp");
        date.put("formats", List.of("yyyy-MM-dd HH:mm:ss"));
        date.put("target_field", "@timestamp");
        date.put("ignore_failure", true);
        dateProcessor.put("date", date);
        processors.add(dateProcessor);

        // Remove original timestamp field
        Map<String, Object> removeProcessor = new HashMap<>();
        Map<String, Object> remove = new HashMap<>();
        remove.put("field", "timestamp");
        remove.put("ignore_failure", true);
        removeProcessor.put("remove", remove);
        processors.add(removeProcessor);

        pipeline.put("processors", processors);

        String pipelineJson = objectMapper.writeValueAsString(pipeline);

        HttpPut putRequest = new HttpPut(esHost + "/_ingest/pipeline/" + pipelineName);
        putRequest.setHeader("Content-Type", "application/json");
        putRequest.setEntity(new StringEntity(pipelineJson));
        return putRequest;
    }

    public static void initElasticConfiguration() {
        try {
            CloseableHttpClient client = HttpClients.createDefault();
            client.execute(setUpRequest());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
