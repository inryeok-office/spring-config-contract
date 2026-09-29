package io.github.inryeokoffice.configcontract.spring.fixtures;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("fixture.java-bean")
public class JavaBeanFixture {
    private String url;
    private int maxPoolSize = 10;
    private List<String> hosts;

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public int getMaxPoolSize() {
        return maxPoolSize;
    }

    public void setMaxPoolSize(int maxPoolSize) {
        this.maxPoolSize = maxPoolSize;
    }

    public List<String> getHosts() {
        return hosts;
    }

    public void setHosts(List<String> hosts) {
        this.hosts = hosts;
    }

    /** Read-only, so Spring cannot bind it and it is not a configuration key. */
    public String getDescription() {
        return url + ":" + maxPoolSize;
    }
}
