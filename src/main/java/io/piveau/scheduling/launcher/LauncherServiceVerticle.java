package io.piveau.scheduling.launcher;

import io.piveau.pipe.PiveauCluster;
import io.piveau.scheduling.quartz.QuartzService;
import io.piveau.utils.ConfigHelper;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.serviceproxy.ServiceBinder;

public class LauncherServiceVerticle extends AbstractVerticle {

    @Override
    public void start(Promise<Void> startPromise) {
        JsonObject clusterConfig = ConfigHelper.forConfig(config()).forceJsonObject("PIVEAU_CLUSTER_CONFIG");
        PiveauCluster.init(vertx, clusterConfig).compose(cluster -> {
            Promise<Void> promise = Promise.promise();
            LauncherService.create(cluster, ready -> {
                if (ready.succeeded()) {
                    new ServiceBinder(vertx).setAddress(LauncherService.SERVICE_ADDRESS).register(LauncherService.class, ready.result());
                    promise.complete();
                } else {
                    promise.fail(ready.cause());
                }
            });
            return promise.future();
        }).setHandler(startPromise);
    }

}
