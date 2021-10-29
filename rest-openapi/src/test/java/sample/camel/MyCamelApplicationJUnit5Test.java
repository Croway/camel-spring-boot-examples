package sample.camel;

import org.apache.camel.example.springboot.Application;
import org.apache.camel.test.spring.junit5.CamelSpringBootTest;

import org.junit.jupiter.api.Test;

import org.assertj.core.api.Assertions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.server.LocalServerPort;

@CamelSpringBootTest
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, classes = {Application.class})
public class MyCamelApplicationJUnit5Test {

	private static final Logger LOG = LoggerFactory.getLogger(MyCamelApplicationJUnit5Test.class);

	@LocalServerPort
	private int port;

	@Autowired
	private TestRestTemplate restTemplate;

	@Test
	public void testOpenApiDocs() throws Exception {
		String contextPath = "/camel/api";

		String openApi = this.restTemplate.getForObject(
				"http://localhost:" + port + contextPath + "/api-docs",
				String.class);

		LOG.info(openApi);

		String expected = "\"servers\" : [ {\n" +
				"    \"url\" : \"" + contextPath + "\"\n" +
				"  } ],";

		Assertions.assertThat(openApi).contains(expected);
	}

}
