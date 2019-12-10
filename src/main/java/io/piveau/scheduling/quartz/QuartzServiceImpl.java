package io.piveau.scheduling.quartz;

import io.piveau.scheduling.launcher.LauncherService;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.quartz.*;
import org.quartz.impl.StdSchedulerFactory;
import org.quartz.impl.matchers.GroupMatcher;
import org.quartz.spi.JobFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

import static org.quartz.JobBuilder.newJob;
import static org.quartz.JobKey.jobKey;
import static org.quartz.TriggerBuilder.newTrigger;

public class QuartzServiceImpl implements QuartzService {

    private final Logger log = LoggerFactory.getLogger(getClass());

    private Scheduler scheduler;

//    private VertxJobFactory jobFactory;

    private LauncherService launcherService;

    QuartzServiceImpl(JobFactory jobFactory, LauncherService launcherService, Handler<AsyncResult<QuartzService>> readyHandler) {
        this.launcherService = launcherService;
        try {
            scheduler = new StdSchedulerFactory().getScheduler();
            scheduler.setJobFactory(jobFactory);
            scheduler.start();
            readyHandler.handle(Future.succeededFuture(this));
        } catch (SchedulerException e) {
            log.error("Creating and starting quartz scheduler", e);
            readyHandler.handle(Future.failedFuture(e));
        }
    }

    @Override
    public QuartzService listTriggers(Handler<AsyncResult<JsonObject>> handler) {
        JsonObject triggers = new JsonObject();
        try {
            scheduler.getTriggerGroupNames().forEach(group -> {
                JsonArray triggerArray = getTriggers(group);
                triggers.put(group, triggerArray);
            });
            handler.handle(Future.succeededFuture(triggers));
        } catch (SchedulerException e) {
            log.error("Get trigger key list", e);
            handler.handle(Future.failedFuture(e));
        }
        return this;
    }

    @Override
    public QuartzService getTriggers(String pipeId, Handler<AsyncResult<JsonArray>> handler) {
        JsonArray triggerArray = getTriggers(pipeId);
        if (triggerArray.isEmpty()) {
            handler.handle(Future.failedFuture("No trigger with id " + pipeId + " found"));
        } else {
            handler.handle(Future.succeededFuture(triggerArray));
        }
        return this;
    }

    @Override
    public QuartzService createOrUpdateTrigger(String pipeId, JsonArray triggerArray, Handler<AsyncResult<String>> handler) {
        launcherService.isPipeAvailable(pipeId, ar -> {
            if (ar.failed() || !ar.result()) {
                handler.handle(Future.failedFuture("No pipe for id " + pipeId + " found"));
            } else {
                JobKey jobKey = jobKey(pipeId, pipeId);
                try {
                    // check if an immediate trigger is contained
                    Trigger now = createImmediateTrigger(pipeId, triggerArray);

                    Set<Trigger> triggers = new HashSet<>();
                    triggerArray.forEach(obj -> triggers.addAll(createTrigger(pipeId, (JsonObject)obj)));

                    String status = "created";
                    JobDetail detail = newJob(PipeJob.class).withIdentity(pipeId, pipeId).build();

                    if (!triggers.isEmpty()) {
                        if (scheduler.checkExists(jobKey)) {
                            scheduler.deleteJob(jobKey);
                            status = "updated";
                        }

                        // create
                        scheduler.scheduleJob(detail, triggers, true);
                    }
                    if (now != null) {
                        if (scheduler.checkExists(jobKey)) {
                            scheduler.triggerJob(jobKey, now.getJobDataMap());
                        } else {
                            scheduler.scheduleJob(detail, now);
                        }
                    }
                    handler.handle(Future.succeededFuture(status));
                } catch (SchedulerException e) {
                    log.error("Scheduling", e);
                    handler.handle(Future.failedFuture(e));
                }
            }
        });

        return this;
    }

    @Override
    public QuartzService deleteTriggers(String pipeId, Handler<AsyncResult<Void>> handler) {
        JobKey jobKey = jobKey(pipeId, pipeId);
        try {
            if (scheduler.checkExists(jobKey)) {
                scheduler.deleteJob(jobKey);
                handler.handle(Future.succeededFuture());
            } else {
                handler.handle(Future.failedFuture("No trigger with id " + pipeId + " found"));
            }
        } catch (SchedulerException e) {
            handler.handle(Future.failedFuture(e));
        }
        return this;
    }

    @Override
    public QuartzService setTriggerStatus(String pipeId, String triggerId, String status, Handler<AsyncResult<String>> handler) {
        try {
            if (!scheduler.checkExists(jobKey(pipeId, pipeId))) {
                handler.handle(Future.failedFuture("pipeId not found"));
            } else if (!scheduler.checkExists(TriggerKey.triggerKey(triggerId, pipeId))) {
                handler.handle(Future.failedFuture("triggerId not found"));
            } else {
                TriggerKey triggerKey = TriggerKey.triggerKey(triggerId, pipeId);
                Trigger.TriggerState state = scheduler.getTriggerState(triggerKey);
                String oldStatus = state == Trigger.TriggerState.PAUSED ? "disabled" : "enabled";
                if (status.equals("enable") && state == Trigger.TriggerState.PAUSED) {
                    scheduler.resumeTrigger(triggerKey);
                    handler.handle(Future.succeededFuture(oldStatus));
                } else if (status.equals("disable") && state != Trigger.TriggerState.PAUSED) {
                    scheduler.pauseTrigger(triggerKey);
                    handler.handle(Future.succeededFuture(oldStatus));
                } else {
                    handler.handle(Future.failedFuture("status already set or unknown"));
                }
            }
        } catch (SchedulerException e) {
            handler.handle(Future.failedFuture(e));
        }
        return this;
    }

    private Set<Trigger> createTrigger(String key, JsonObject triggerObject) {
        Set<Trigger> triggers = new HashSet<>();

        String id = triggerObject.getString("id");
        if (triggerObject.containsKey("interval")) {

            JsonObject interval = triggerObject.getJsonObject("interval");
            String unit = interval.getString("unit");
            int value = interval.getInteger("value");
            CalendarIntervalScheduleBuilder scheduleBuilder = CalendarIntervalScheduleBuilder.calendarIntervalSchedule().withInterval(value, DateBuilder.IntervalUnit.valueOf(unit));
            TriggerBuilder<CalendarIntervalTrigger> builder = newTrigger()
                    .withIdentity(id, key)
                    .usingJobData("triggerObject", triggerObject.encodePrettily())
                    .withSchedule(scheduleBuilder.withMisfireHandlingInstructionDoNothing());

            evaluateNext(triggerObject, builder);

            triggers.add(builder.build());
        } else if (triggerObject.containsKey("cron")) {

            String cron = triggerObject.getString("cron");
            CronScheduleBuilder scheduleBuilder = CronScheduleBuilder.cronSchedule(cron);
            TriggerBuilder<CronTrigger> builder = newTrigger()
                    .withIdentity(id, key)
                    .usingJobData("triggerObject", triggerObject.encodePrettily())
                    .withSchedule(scheduleBuilder.withMisfireHandlingInstructionDoNothing());

            evaluateNext(triggerObject, builder);

            triggers.add(builder.build());
        } else if (triggerObject.containsKey("specific")) {
            JsonArray specifics = triggerObject.getJsonArray("specific");
            String triggerKey = id;
            int count = 1;
            Iterator<Object> it = specifics.iterator();
            while (it.hasNext()) {
                String dateTime = it.next().toString();
                TriggerBuilder<Trigger> builder = newTrigger()
                        .withIdentity(triggerKey, key)
                        .usingJobData("triggerObject", triggerObject.encodePrettily());
                triggerKey = id + ++count;
                Date start = Date.from(ZonedDateTime.parse(dateTime, DateTimeFormatter.ISO_DATE_TIME).toInstant());
                builder.startAt(start);
                triggers.add(builder.build());
            }
        } else {
            TriggerBuilder<Trigger> builder = newTrigger().withIdentity(id, key).usingJobData("triggerObject", triggerObject.encodePrettily());
            triggers.add(builder.build());
        }
        return triggers;
    }

    private void evaluateNext(JsonObject triggerObject, TriggerBuilder triggerBuilder) {
        Date next = triggerObject.containsKey("next") ? Date.from(ZonedDateTime.parse(triggerObject.getString("next")).toInstant()) : DateBuilder.futureDate(5, DateBuilder.IntervalUnit.MINUTE);
        triggerBuilder.startAt(next);
    }

    private JsonArray getTriggers(String pipeId) {
        JsonArray triggerArray = new JsonArray();
        try {
            Set<TriggerKey> groupTriggers = scheduler.getTriggerKeys(GroupMatcher.groupEquals(pipeId));
            groupTriggers.iterator().forEachRemaining(key -> {
                try {
                    Trigger trigger = scheduler.getTrigger(key);
                    JsonObject triggerObject = new JsonObject(trigger.getJobDataMap().getString("triggerObject"));
                    if (triggerObject.containsKey("next")) {
                        triggerObject.put("next", trigger.getFireTimeAfter(new Date()).toInstant().atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
                    }
                    triggerObject.put("status", (scheduler.getTriggerState(key) == Trigger.TriggerState.PAUSED ? "disabled": "enabled"));
                    triggerArray.add(triggerObject);
                } catch (SchedulerException e) {
                    log.error("Get trigger from key", e);
                }
            });
            return triggerArray;
        } catch (SchedulerException e) {
            log.error("Get triggers", e);
            return triggerArray;
        }
    }

    private Trigger createImmediateTrigger(String key, JsonArray triggers) {
        List<JsonObject> immediates = triggers.stream().filter(obj -> {
            JsonObject trigger = (JsonObject)obj;
            return (!trigger.containsKey("interval") && !trigger.containsKey("cron") && !trigger.containsKey("specific"));
        }).map(o -> (JsonObject)o).collect(Collectors.toList());

        if (immediates.isEmpty()) {
            return null;
        } else {
            for (Object trigger : immediates) {
                triggers.remove(trigger);
            }
            JsonObject immediate = immediates.get(immediates.size() - 1);
            String id = immediate.getString("id");
            TriggerBuilder<Trigger> builder = newTrigger().withIdentity(id, key).usingJobData("triggerObject", immediate.encodePrettily());
            return builder.build();
        }
    }

}
