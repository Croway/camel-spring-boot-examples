/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.example.mention;

import java.lang.Class;
import java.lang.reflect.Method;
import org.apache.camel.ExchangePattern;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.amqp.AMQPComponent;
import org.apache.qpid.jms.JmsConnectionFactory;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

@Component
public class AmqpSalesforceRoute extends RouteBuilder {

    @Autowired
    JmsConnectionFactory amqpConnectionFactory;

    @Bean
    public AMQPComponent amqpConnection() {
        AMQPComponent amqp = new AMQPComponent();
        amqp.setConnectionFactory(amqpConnectionFactory);
        return amqp;
    }

    @Override
    public void configure() throws Exception {

        restConfiguration().component("servlet");

        rest().post("/").to("direct:send");
        from("direct:send")
                .setExchangePattern(ExchangePattern.InOnly)
                .to("amqp:queue:example")
                .log("Message ${body} sent to AMQP queue");

        from("amqp:queue:example")
                .log("Received message from AMQP queue: ${body}")
                .process(exchange -> {
                    String jsonMessage = exchange.getIn().getBody(String.class);
                    try (JsonParser jsonParser = new JsonFactory().createParser(jsonMessage)) {
                        String name = "no name";
                        String screenName = "no description";
                        while(jsonParser.nextToken() != JsonToken.END_OBJECT) {
                            String key = jsonParser.getCurrentName();
                            if ("lastName".equals(key)) {
                                jsonParser.nextToken();
                                name = jsonParser.getValueAsString();
                            } else if ("screenName".equals(key)) {
                                jsonParser.nextToken();
                                screenName = jsonParser.getValueAsString();
                            }
                        }

                        Class contact = null;
                        if (Class.forName("org.apache.camel.salesforce.dto.Contact") != null) {
                            contact = Class.forName("org.apache.camel.salesforce.dto.Contact");
                        }

                        Object contactObject = contact.newInstance();
                        Method setLastName = contact.getMethod("setLastName", String.class);
                        Method setTwitterScreenName__c = contact.getMethod("setTwitterScreenName__c", String.class);
                        setLastName.invoke(contactObject, name);
                        setTwitterScreenName__c.invoke(contactObject, screenName);
                        exchange.getIn().setBody(contactObject);
                    }
                })
                .to("salesforce:upsertSObject?sObjectIdName=TwitterScreenName__c")
                .log("SObject ID: ${body?.id}");

    }

}
