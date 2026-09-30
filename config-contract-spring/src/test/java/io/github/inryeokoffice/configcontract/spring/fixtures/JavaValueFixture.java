package io.github.inryeokoffice.configcontract.spring.fixtures;

import org.springframework.beans.factory.annotation.Value;

public class JavaValueFixture {
    @Value("${fixture.java-value.field}")
    private String field;

    private String setterValue;

    public JavaValueFixture(@Value("${fixture.java-value.constructor:fallback}") String constructorValue) {
    }

    @Value("${fixture.java-value.setter}")
    public void setSetterValue(String setterValue) {
        this.setterValue = setterValue;
    }
}
