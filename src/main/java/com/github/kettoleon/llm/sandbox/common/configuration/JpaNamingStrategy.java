package com.github.kettoleon.llm.sandbox.common.configuration;

import org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy;
import org.hibernate.boot.model.naming.Identifier;
import org.hibernate.engine.jdbc.env.spi.JdbcEnvironment;

public class JpaNamingStrategy extends CamelCaseToUnderscoresNamingStrategy {

    @Override
    public Identifier toPhysicalTableName(Identifier name, JdbcEnvironment jdbcEnvironment) {
        return super.toPhysicalTableName(new Identifier("SANDBOX_" + name.getText(), false), jdbcEnvironment);
    }
}
