package com.group2.flightres.service;

import com.group2.flightres.model.User;

/**
 * Sends transactional welcome emails without blocking or failing registration.
 */
public interface EmailService {

    /**
     * Asynchronously dispatch a welcome email for a newly registered customer.
     *
     * @param user    persisted customer account
     * @param baseUrl public web base URL used for links in the message
     */
    void sendWelcome(User user, String baseUrl);
}
