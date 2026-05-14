package dev.autotix.application.customer;

import dev.autotix.domain.customer.Customer;
import dev.autotix.domain.customer.CustomerRepository;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Use case: list/search customers.
 */
@Service
public class ListCustomersUseCase {

    private final CustomerRepository customerRepository;

    public ListCustomersUseCase(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    public List<Customer> list(String q, int offset, int limit) {
        if (q == null || q.trim().isEmpty()) {
            return customerRepository.findAll(offset, limit);
        }
        return customerRepository.searchByText(q.trim(), offset, limit);
    }
}
