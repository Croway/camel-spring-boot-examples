package sample.camel;

import org.apache.camel.test.AvailablePortFinder;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

@Testcontainers
public class ClusteredQuartzTest {

	@Container
	private static final PostgreSQLContainer POSTGRES_SQL_CONTAINER = new PostgreSQLContainer()
			.withDatabaseName("quartz_schedule")
			.withUsername("postgres")
			.withPassword("postgres");

	@Test
	@DisplayName("Body is logged only once")
	public void test() throws InterruptedException, SQLException, IOException {
		createSchema();

		int firstInstancePort = AvailablePortFinder.getNextAvailable();
		SpringApplicationBuilder firstInstance = new SpringApplicationBuilder(Application.class)
				.properties("server.port=" + firstInstancePort,
						"camel.springboot.name=firstInstance",
						"camel.component.quartz.scheduler=quartzScheduler",
						"jdbc.url=" + POSTGRES_SQL_CONTAINER.getJdbcUrl(),
						"jdbc.user=" + POSTGRES_SQL_CONTAINER.getUsername(),
						"jdbc.password=" + POSTGRES_SQL_CONTAINER.getPassword());

		int secondInstancePort = AvailablePortFinder.getNextAvailable();
		SpringApplicationBuilder secondInstance = new SpringApplicationBuilder(Application.class)
				.properties("server.port=" + secondInstancePort,
						"camel.springboot.name=secondInstance",
						"camel.component.quartz.scheduler=quartzScheduler",
						"jdbc.url=" + POSTGRES_SQL_CONTAINER.getJdbcUrl(),
						"jdbc.user=" + POSTGRES_SQL_CONTAINER.getUsername(),
						"jdbc.password=" + POSTGRES_SQL_CONTAINER.getPassword());

		ConfigurableApplicationContext applicationContext = firstInstance.run();
		secondInstance.run();

		Thread.sleep(20000);

		applicationContext.getBean("quartzScheduler");
	}

	private static void createSchema() throws SQLException, IOException {
		PGSimpleDataSource ds = new PGSimpleDataSource();
		ds.setUser(POSTGRES_SQL_CONTAINER.getUsername());
		ds.setURL(POSTGRES_SQL_CONTAINER.getJdbcUrl());
		ds.setPassword(POSTGRES_SQL_CONTAINER.getPassword());
		ds.setDatabaseName(POSTGRES_SQL_CONTAINER.getDatabaseName());

		try (Connection connection = ds.getConnection();
			 Statement statement = connection.createStatement();) {
			String query = new String(ClusteredQuartzTest.class.getResourceAsStream("/db_schema.sql").readAllBytes(), StandardCharsets.UTF_8);
			statement.executeUpdate(query);
		}
	}
}
