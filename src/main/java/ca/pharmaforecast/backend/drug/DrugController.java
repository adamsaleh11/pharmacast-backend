package ca.pharmaforecast.backend.drug;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/drugs")
public class DrugController {

    private final DrugLookupService drugLookupService;

    public DrugController(DrugLookupService drugLookupService) {
        this.drugLookupService = drugLookupService;
    }

    @GetMapping("/{din}")
    public DrugResponse getDrug(@PathVariable String din) {
        try {
            return drugLookupService.getByDin(din);
        } catch (InvalidDinException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }
    }
}
