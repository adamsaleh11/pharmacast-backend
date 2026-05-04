package ca.pharmaforecast.backend.notification;

import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class NotificationJob {

    private final NotificationJobService notificationJobService;

    public NotificationJob(NotificationJobService notificationJobService) {
        this.notificationJobService = notificationJobService;
    }

    @Async("notificationJobExecutor")
    @Scheduled(cron = "${pharmaforecast.jobs.daily-notification-cron}")
    public void runDailyNotificationCheck() {
        notificationJobService.runDailyNotificationCheck();
    }

    @Async("notificationJobExecutor")
    @Scheduled(cron = "${pharmaforecast.jobs.daily-digest-cron}")
    public void sendDailyDigests() {
        notificationJobService.sendDailyDigests();
    }

    @Async("notificationJobExecutor")
    @Scheduled(cron = "${pharmaforecast.jobs.weekly-insights-cron}")
    public void sendWeeklyInsights() {
        notificationJobService.sendWeeklyInsights();
    }
}
