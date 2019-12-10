package io.piveau.scheduling.quartz;

import io.piveau.pipe.PipeLauncher;
import io.piveau.scheduling.launcher.LauncherService;
import io.vertx.core.json.JsonObject;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@DisallowConcurrentExecution
public class PipeJob implements Job {

    private final Logger log = LoggerFactory.getLogger(getClass());

    private LauncherService launcherService;
    private String pipeName;

    public PipeJob(LauncherService launcherService, String pipeName) {
        this.launcherService = launcherService;
        this.pipeName = pipeName;
    }

    @Override
    public void execute(JobExecutionContext jobExecutionContext) throws JobExecutionException {

        String triggerObject = jobExecutionContext.getMergedJobDataMap().getString("triggerObject");
        log.debug("Job triggered: {}", triggerObject);

        JsonObject trigger = new JsonObject(triggerObject);

        JsonObject configs = trigger.getJsonObject("configs", new JsonObject());

        launcherService.launch(pipeName, configs, ar -> {
            if (ar.succeeded()) {
                log.debug("Pipe {} started successfully!", pipeName);
            } else {
                log.error("Starting pipe " + pipeName + " failed!", ar.cause());
            }
        });

    }

}
