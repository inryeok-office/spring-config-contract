package io.github.inryeokoffice.configcontract.spring.fixtures;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Setters with each visibility, to compare discovery with what Spring Boot binds. */
@ConfigurationProperties("crosscheck.setter-visibility")
public class SetterVisibilityProperties {
    private String publicValue;
    private String packagePrivateValue;
    private String protectedValue;
    private String privateValue;

    public void setPublicValue(String publicValue) {
        this.publicValue = publicValue;
    }

    void setPackagePrivateValue(String packagePrivateValue) {
        this.packagePrivateValue = packagePrivateValue;
    }

    protected void setProtectedValue(String protectedValue) {
        this.protectedValue = protectedValue;
    }

    private void setPrivateValue(String privateValue) {
        this.privateValue = privateValue;
    }

    /** Not an accessor name, so it does not create a bindable property. */
    public String boundValue(String property) {
        return switch (property) {
            case "public-value" -> publicValue;
            case "package-private-value" -> packagePrivateValue;
            case "protected-value" -> protectedValue;
            case "private-value" -> privateValue;
            default -> throw new IllegalArgumentException(property);
        };
    }
}
