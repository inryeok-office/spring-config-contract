package io.github.inryeokoffice.configcontract.spring.spike;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("spike.java-bean")
public class JavaBeanProperties {
    private Integer maxPoolSize;

    private String name = "java-default";

    public Integer getMaxPoolSize() {
        return maxPoolSize;
    }

    public void setMaxPoolSize(Integer maxPoolSize) {
        this.maxPoolSize = maxPoolSize;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
