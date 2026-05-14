package dev.autotix.application.customer;

import dev.autotix.domain.AutotixException;
import dev.autotix.domain.customer.Customer;
import dev.autotix.domain.customer.CustomerId;
import dev.autotix.domain.customer.CustomerRepository;
import dev.autotix.domain.ticket.Ticket;
import dev.autotix.domain.ticket.TicketRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Use case: get a customer by id, including recent ticket ids.
 */
@Service
public class GetCustomerUseCase {

    private final CustomerRepository customerRepository;
    private final TicketRepository ticketRepository;

    public GetCustomerUseCase(CustomerRepository customerRepository,
                               TicketRepository ticketRepository) {
        this.customerRepository = customerRepository;
        this.ticketRepository = ticketRepository;
    }

    public Result get(Long customerId) {
        CustomerId id = new CustomerId(customerId);
        Customer customer = customerRepository.findById(id)
                .orElseThrow(() -> new AutotixException.NotFoundException("Customer not found: " + customerId));

        List<String> recentTicketIds = ticketRepository.findByCustomerId(id, 10).stream()
                .map(t -> t.id().value())
                .collect(Collectors.toList());

        return new Result(customer, recentTicketIds);
    }

    public static class Result {
        public final Customer customer;
        public final List<String> recentTicketIds;

        public Result(Customer customer, List<String> recentTicketIds) {
            this.customer = customer;
            this.recentTicketIds = recentTicketIds;
        }
    }
}
