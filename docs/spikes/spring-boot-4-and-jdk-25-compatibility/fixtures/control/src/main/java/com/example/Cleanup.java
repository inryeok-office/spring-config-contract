package com.example;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class Cleanup {

    @Scheduled(cron = "${app.cleanup.cron}", zone = "${app.cleanup.zone:UTC}")
    public void purge() {
    }
}
