package ca.pharmaforecast.backend;

import ca.pharmaforecast.backend.drug.HealthCanadaApiClient;
import ca.pharmaforecast.backend.drug.HealthCanadaDrugProduct;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.http.HttpMethod.GET;

class HealthCanadaApiClientTest {

    @Test
    void fetchDrugProductReturnsFirstResultFromHealthCanadaArray() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl("https://health-products.canada.ca/api/drug");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        HealthCanadaApiClient client = new HealthCanadaApiClient(builder.build());

        server.expect(requestTo("https://health-products.canada.ca/api/drug/drugproduct/?lang=en&type=json&din=00012345"))
                .andExpect(method(GET))
                .andRespond(withSuccess("""
                        [{
                          "drug_code": 456,
                          "drug_identification_number": "00012345",
                          "brand_name": "ATORVASTATIN",
                          "company_name": "APOTEX INC",
                          "class_name": "Human"
                        }]
                        """, MediaType.APPLICATION_JSON));

        Optional<HealthCanadaDrugProduct> result = client.fetchDrugProduct("00012345");

        assertThat(result).isPresent();
        assertThat(result.get().drugCode()).isEqualTo("456");
        assertThat(result.get().brandName()).isEqualTo("ATORVASTATIN");
        server.verify();
    }

    @Test
    void fetchDrugProductReturnsEmptyForNotFound() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl("https://health-products.canada.ca/api/drug");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        HealthCanadaApiClient client = new HealthCanadaApiClient(builder.build());

        server.expect(requestTo("https://health-products.canada.ca/api/drug/drugproduct/?lang=en&type=json&din=00099999"))
                .andExpect(method(GET))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThat(client.fetchDrugProduct("00099999")).isEmpty();
        server.verify();
    }
}
