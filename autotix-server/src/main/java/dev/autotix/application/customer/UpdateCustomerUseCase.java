package dev.autotix.application.customer;

import dev.autotix.domain.AutotixException;
import dev.autotix.domain.customer.Customer;
import dev.autotix.domain.customer.CustomerId;
import dev.autotix.domain.customer.CustomerRepository;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Use case: update customer displayName, primaryEmail, and attributes.
 * Identifiers are auto-managed and cannot be modified here.
 */
@Service
public class UpdateCustomerUseCase {

    private final CustomerRepository customerRepository;

    public UpdateCustomerUseCase(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    public Customer update(Long customerId, String displayName, String primaryEmail,
                           Map<String, String> attributes) {
        CustomerId id = new CustomerId(customerId);
        Customer customer = customerRepository.findById(id)
                .orElseThrow(() -> new AutotixException.NotFoundException("Customer not found: " + customerId));

        // Apply updates using reflection on the package-private fields via domain methods
        if (displayName != null) {
            customer.updateDisplayName(displayName);
        }
        if (primaryEmail != null) {
            customer.updatePrimaryEmail(primaryEmail);
        }
        if (attributes != null) {
            // Clear and re-set all attributes
            for (Map.Entry<String, String> entry : attributes.entrySet()) {
                customer.setAttribute(entry.getKey(), entry.getValue());
            }
        }
        customerRepository.save(customer);
        return customer;
    }
}
