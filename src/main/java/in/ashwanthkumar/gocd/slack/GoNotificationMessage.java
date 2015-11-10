package in.ashwanthkumar.gocd.slack;

import com.google.gson.annotations.SerializedName;
import com.thoughtworks.go.plugin.api.logging.Logger;

import java.net.URI;
import java.net.URISyntaxException;

public class GoNotificationMessage {
    private Logger LOG = Logger.getLoggerFor(GoNotificationMessage.class);

    /**
     * Raised when we can't find information about our build in the array
     * returned by the server.
     */
    static public class BuildDetailsNotFoundException extends Exception {
        public BuildDetailsNotFoundException(String pipelineName,
                                             int pipelineCounter)
        {
            super(String.format("could not find details for %s/%d",
                                pipelineName, pipelineCounter));
        }
    }

    static class Stage {
        @SerializedName("name")
        private String name;

        @SerializedName("counter")
        private String counter;

        @SerializedName("state")
        private String state;

        @SerializedName("result")
        private String result;

        @SerializedName("create-time")
        private String createTime;

        @SerializedName("last-transition-time")
        private String lastTransitionTime;
    }

    static class Pipeline {
        @SerializedName("name")
        private String name;

        @SerializedName("counter")
        private String counter;

        @SerializedName("stage")
        private Stage stage;
    }

    @SerializedName("pipeline")
    private Pipeline pipeline;

    public String goServerUrl(String host) throws URISyntaxException {
        return new URI(String.format("%s/go/pipelines/%s/%s/%s/%s", host, pipeline.name, pipeline.counter, pipeline.stage.name, pipeline.stage.counter)).normalize().toASCIIString();
    }

    public String goHistoryUrl(String host) throws URISyntaxException {
        return new URI(String.format("%s/go/api/pipelines/%s/history", host, pipeline.name)).normalize().toASCIIString();
    }

    public String fullyQualifiedJobName() {
        return pipeline.name + "/" + pipeline.counter + "/" + pipeline.stage.name + "/" + pipeline.stage.counter;
    }

    public String getPipelineName() {
        return pipeline.name;
    }

    public String getPipelineCounter() {
        return pipeline.counter;
    }

    public String getStageName() {
        return pipeline.stage.name;
    }

    public String getStageCounter() {
        return pipeline.stage.counter;
    }

    public String getStageState() {
        return pipeline.stage.state;
    }

    public String getStageResult() {
        return pipeline.stage.result;
    }

    public String getCreateTime() {
        return pipeline.stage.createTime;
    }

    public String getLastTransitionTime() {
        return pipeline.stage.lastTransitionTime;
    }

}
