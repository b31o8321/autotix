package dev.autotix.infrastructure.persistence.customer;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import dev.autotix.domain.channel.ChannelId;
import dev.autotix.domain.customer.Customer;
import dev.autotix.domain.customer.CustomerId;
import dev.autotix.domain.customer.CustomerIdentifier;
import dev.autotix.domain.customer.CustomerIdentifierType;
import dev.autotix.domain.customer.CustomerRepository;
import dev.autotix.infrastructure.persistence.customer.mapper.CustomerIdentifierMapper;
import dev.autotix.infrastructure.persistence.customer.mapper.CustomerMapper;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Implements CustomerRepository port using MyBatis Plus.
 *
 * Identifiers are stored in a separate customer_identifier table.
 * On save: inserts new identifiers (by diff with existing ones).
 * On find: loads the customer row + all identifier rows, then rehydrates.
 */
@Repository
public class CustomerRepositoryImpl implements CustomerRepository {

    private final CustomerMapper customerMapper;
    private final CustomerIdentifierMapper identifierMapper;

    public CustomerRepositoryImpl(CustomerMapper customerMapper,
                                  CustomerIdentifierMapper identifierMapper) {
        this.customerMapper = customerMapper;
        this.identifierMapper = identifierMapper;
    }

    @Override
    public CustomerId save(Customer customer) {
        CustomerEntity entity = toCustomerEntity(customer);
        if (customer.id() == null) {
            entity.setId(null);
            entity.setCreatedAt(Instant.now());
            entity.setUpdatedAt(Instant.now());
            customerMapper.insert(entity);
            CustomerId newId = new CustomerId(entity.getId());
            customer.assignPersistedId(newId);
            // Insert all identifiers as new
            insertIdentifiers(entity.getId(), customer.identifiers(), Collections.emptyList());
        } else {
            entity.setUpdatedAt(Instant.now());
            customerMapper.updateById(entity);
            Long dbId = customer.id().longValue();
            List<CustomerIdentifierEntity> existing = loadIdentifierEntities(dbId);
            insertIdentifiers(dbId, customer.identifiers(), existing);
        }
        return customer.id();
    }

    @Override
    public Optional<Customer> findById(CustomerId id) {
        Long dbId = id.longValue();
        CustomerEntity entity = customerMapper.selectById(dbId);
        if (entity == null) {
            return Optional.empty();
        }
        List<CustomerIdentifierEntity> identifierEntities = loadIdentifierEntities(dbId);
        return Optional.of(toDomain(entity, identifierEntities));
    }

    @Override
    public Optional<Customer> findByIdentifier(CustomerIdentifierType type, String value) {
        String normalized = type == CustomerIdentifierType.EMAIL
                ? value.trim().toLowerCase() : value.trim();

        QueryWrapper<CustomerIdentifierEntity> qw = new QueryWrapper<>();
        qw.eq("identifier_type", type.name())
          .eq("identifier_value", normalized)
          .last("LIMIT 1");
        CustomerIdentifierEntity identEnt = identifierMapper.selectOne(qw);
        if (identEnt == null) {
            return Optional.empty();
        }
        return findById(new CustomerId(identEnt.getCustomerId()));
    }

    @Override
    public List<Customer> findAll(int offset, int limit) {
        QueryWrapper<CustomerEntity> qw = new QueryWrapper<>();
        qw.orderByAsc("id").last("LIMIT " + limit + " OFFSET " + offset);
        List<CustomerEntity> entities = customerMapper.selectList(qw);
        return entities.stream()
                .map(e -> toDomain(e, loadIdentifierEntities(e.getId())))
                .collect(Collectors.toList());
    }

    @Override
    public List<Customer> searchByText(String q, int offset, int limit) {
        if (q == null || q.trim().isEmpty()) {
            return findAll(offset, limit);
        }
        String pattern = "%" + q.trim() + "%";
        QueryWrapper<CustomerEntity> qw = new QueryWrapper<>();
        qw.like("display_name", q.trim())
          .or().like("primary_email", q.trim())
          .orderByAsc("id")
          .last("LIMIT " + limit + " OFFSET " + offset);
        List<CustomerEntity> entities = customerMapper.selectList(qw);
        return entities.stream()
                .map(e -> toDomain(e, loadIdentifierEntities(e.getId())))
                .collect(Collectors.toList());
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    private void insertIdentifiers(Long customerId, List<CustomerIdentifier> domainIdentifiers,
                                   List<CustomerIdentifierEntity> existingEntities) {
        // Build a set of existing (type, value) pairs
        Set<String> existingKeys = new HashSet<>();
        for (CustomerIdentifierEntity e : existingEntities) {
            existingKeys.add(e.getIdentifierType() + "|" + e.getIdentifierValue());
        }
        for (CustomerIdentifier ci : domainIdentifiers) {
            String key = ci.type().name() + "|" + ci.value();
            if (!existingKeys.contains(key)) {
                CustomerIdentifierEntity entity = new CustomerIdentifierEntity();
                entity.setCustomerId(customerId);
                entity.setIdentifierType(ci.type().name());
                entity.setIdentifierValue(ci.value());
                entity.setChannelId(ci.channelId() != null ? ci.channelId().value() : null);
                entity.setFirstSeenAt(ci.firstSeenAt());
                identifierMapper.insert(entity);
            }
        }
    }

    private List<CustomerIdentifierEntity> loadIdentifierEntities(Long customerId) {
        QueryWrapper<CustomerIdentifierEntity> qw = new QueryWrapper<>();
        qw.eq("customer_id", customerId);
        return identifierMapper.selectList(qw);
    }

    private CustomerEntity toCustomerEntity(Customer c) {
        CustomerEntity e = new CustomerEntity();
        if (c.id() != null) {
            e.setId(c.id().longValue());
        }
        e.setDisplayName(c.displayName());
        e.setPrimaryEmail(c.primaryEmail());
        e.setAttributesJson(c.attributes().isEmpty() ? null : JSON.toJSONString(c.attributes()));
        e.setCreatedAt(c.createdAt());
        e.setUpdatedAt(c.updatedAt());
        return e;
    }

    private Customer toDomain(CustomerEntity e, List<CustomerIdentifierEntity> identifierEntities) {
        Map<String, String> attributes = new HashMap<>();
        if (e.getAttributesJson() != null && !e.getAttributesJson().isEmpty()) {
            attributes = JSON.parseObject(e.getAttributesJson(),
                    new TypeReference<Map<String, String>>() {});
        }
        List<CustomerIdentifier> identifiers = new ArrayList<>();
        for (CustomerIdentifierEntity ie : identifierEntities) {
            ChannelId channelId = ie.getChannelId() != null ? new ChannelId(ie.getChannelId()) : null;
            identifiers.add(new CustomerIdentifier(
                    CustomerIdentifierType.valueOf(ie.getIdentifierType()),
                    ie.getIdentifierValue(),
                    channelId,
                    ie.getFirstSeenAt()));
        }
        return Customer.rehydrate(
                new CustomerId(e.getId()),
                e.getDisplayName(),
                e.getPrimaryEmail(),
                identifiers,
                attributes,
                e.getCreatedAt(),
                e.getUpdatedAt());
    }
}
