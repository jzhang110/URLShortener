package com.schwab.urlshortener.bdd;

import com.schwab.urlshortener.analytics.repository.ClickEventRepository;
import io.cucumber.spring.CucumberContextConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/** Scenarios run against the full application on a random port with the Flyway-managed H2 schema. */
@CucumberContextConfiguration
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class CucumberSpringConfiguration {

    /** Real repository by default; a scenario may make it fail to exercise analytics isolation. */
    @MockitoSpyBean
    ClickEventRepository clickEventRepository;
}
