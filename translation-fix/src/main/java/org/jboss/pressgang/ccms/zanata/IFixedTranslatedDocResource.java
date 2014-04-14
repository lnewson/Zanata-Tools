package org.jboss.pressgang.ccms.zanata;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.jboss.resteasy.client.ClientResponse;
import org.zanata.common.LocaleId;
import org.zanata.rest.RestConstant;
import org.zanata.rest.client.ITranslatedDocResource;

@Produces({MediaType.APPLICATION_XML})
@Consumes({MediaType.APPLICATION_XML})
public interface IFixedTranslatedDocResource extends ITranslatedDocResource {

    @DELETE
    @Path("{id}/translations/{locale}")
    public ClientResponse<String> deleteTranslations(@PathParam("id") String idNoSlash, @PathParam("locale") LocaleId locale, @HeaderParam(
            RestConstant.HEADER_USERNAME) String username, @HeaderParam(RestConstant.HEADER_API_KEY) String apikey);
}
