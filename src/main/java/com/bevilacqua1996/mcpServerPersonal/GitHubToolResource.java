package com.bevilacqua1996.mcpServerPersonal;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

@ApplicationScoped
@Path("/")
public class GitHubToolResource {

    @Inject
    GitHubToolService gitHubToolService;

    @GET
    @Path("/tools/repos")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    public Response listReposStreamable() {
        return streamText(gitHubToolService.listReposBody(), MediaType.APPLICATION_OCTET_STREAM);
    }

    @GET
    @Path("/tools/describe")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    public Response describeRepoStreamable(@QueryParam("name") String repoName) {
        return streamText(gitHubToolService.describeRepoBody(repoName), MediaType.APPLICATION_OCTET_STREAM);
    }

    @GET
    @Path("/tools/documentation/search")
    @Produces(MediaType.TEXT_PLAIN)
    public Response searchMenthoringDocumentationStreamable(
            @QueryParam("keyWords") List<String> keyWords) {
        return streamText(gitHubToolService.searchDocumentationBody(keyWords), MediaType.TEXT_PLAIN);
    }

    private Response streamText(String text, String mediaType) {
        StreamingOutput stream = outputStream -> {
            try (OutputStream out = outputStream) {
                out.write(text.getBytes(StandardCharsets.UTF_8));
                out.flush();
            }
        };
        return Response.ok(stream).type(mediaType).build();
    }
}
