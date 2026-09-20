package io.github.inryeokoffice.configcontract.spring.spike;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("spike.java-record")
public record JavaRecordProperties(String url, @DefaultValue("5") int retries) {
}
