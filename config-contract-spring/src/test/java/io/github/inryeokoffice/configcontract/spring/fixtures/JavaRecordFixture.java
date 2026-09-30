package io.github.inryeokoffice.configcontract.spring.fixtures;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.boot.context.properties.bind.Name;

@ConfigurationProperties("fixture.java-record")
public record JavaRecordFixture(String url, @DefaultValue("5") int retries, @Name("pool") String poolName) {
}
