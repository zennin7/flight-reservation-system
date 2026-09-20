package com.group2.flightres.controller;

import com.group2.flightres.model.BookingContext;

/**
 * Implemented by any controller loaded into the MainShell's center
 * pane. init() receives the shared booking state and a callback the
 * controller invokes once its step is complete, to advance the wizard.
 */
public interface WizardStepController {
    void init(BookingContext context, Runnable onNext);
}

