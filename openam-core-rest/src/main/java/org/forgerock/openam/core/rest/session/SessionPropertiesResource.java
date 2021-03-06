/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions copyright [year] [name of copyright owner]".
 *
 * Copyright 2016 ForgeRock AS.
 */

package org.forgerock.openam.core.rest.session;

import static org.forgerock.json.JsonValue.json;
import static org.forgerock.json.JsonValue.object;
import static org.forgerock.json.resource.Responses.newResourceResponse;
import static org.forgerock.util.promise.Promises.newResultPromise;
import static org.forgerock.json.resource.PatchOperation.OPERATION_REMOVE;
import static org.forgerock.json.resource.PatchOperation.OPERATION_REPLACE;

import javax.inject.Inject;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.iplanet.dpro.session.SessionException;
import com.iplanet.sso.SSOException;
import com.iplanet.sso.SSOToken;
import com.sun.identity.delegation.DelegationException;
import com.sun.identity.idm.AMIdentity;
import com.sun.identity.idm.IdRepoException;
import com.sun.identity.shared.debug.Debug;
import com.sun.identity.sm.DNMapper;
import org.forgerock.http.routing.UriRouterContext;
import org.forgerock.json.JsonValue;
import org.forgerock.json.JsonValueException;
import org.forgerock.json.resource.ActionRequest;
import org.forgerock.json.resource.ActionResponse;
import org.forgerock.json.resource.BadRequestException;
import org.forgerock.json.resource.ForbiddenException;
import org.forgerock.json.resource.InternalServerErrorException;
import org.forgerock.json.resource.PatchOperation;
import org.forgerock.json.resource.PatchRequest;
import org.forgerock.json.resource.ReadRequest;
import org.forgerock.json.resource.ResourceException;
import org.forgerock.json.resource.ResourceResponse;
import org.forgerock.json.resource.SingletonResourceProvider;
import org.forgerock.json.resource.UpdateRequest;
import org.forgerock.openam.rest.RealmContext;
import org.forgerock.openam.rest.RestUtils;
import org.forgerock.openam.rest.resource.SSOTokenContext;
import org.forgerock.openam.session.SessionConstants;
import org.forgerock.openam.session.SessionPropertyWhitelist;
import org.forgerock.services.context.Context;
import org.forgerock.util.promise.Promise;

/**
 * EndPoint for querying the updating the session properties via a Rest interface
 * <p>
 * This endpoint allows GET, PATCH and UPDATE
 *
 * GET expects tokenId Path Parameter
 * Returns:
 * <code>
 *  {'property1' : 'value1', 'property2' : 'value2'}
 * <code/>
 *
 * PATCH expects tokenId Path Parameter, {'patchOperations' : [{'OPERATION' : 'replace', 'property1' : ,newValue, }]}
 * Returns:
 * <code>
 *  {'property1' : 'newValue', 'property2' : 'value2'}
 * <code/>
 *
 * UPDATE expects tokenId Path Parameter, {'property1' : 'newValue1', 'property2' : 'newValue2'}
 * Returns:
 * <code>
 *  {'property1' : 'newValue1', 'property2' : 'newValue2'}
 * <code/>
 *
 *
 * @since 14.0.0
 */
public class SessionPropertiesResource implements SingletonResourceProvider {

    private static final Debug LOGGER = Debug.getInstance(SessionConstants.SESSION_DEBUG);

    /**
     * Path Parameter Name
     */
    public static final String TOKEN_ID_PARAM_NAME = "tokenId";
    private static final Set<String> SUPPORTED_OPERATIONS = new HashSet<>(Arrays.asList(new String[]{
                    PatchOperation.OPERATION_REMOVE,
                    PatchOperation.OPERATION_REPLACE}));
    private final SessionPropertyWhitelist sessionPropertyWhitelist;
    private final SessionUtilsWrapper sessionUtilsWrapper;
    private final SessionResourceUtil sessionResourceUtil;

    /**
     * Constructs a new instance of the SessionPropertiesResource
     *
     *  @param sessionPropertyWhitelist An instance of the SessionPropertyWhitelist.
     * @param sessionUtilsWrapper An instance of SessionUtilsWrapper.
     * @param sessionResourceUtil An instance of SessionResourceUtil.
     */
    @Inject
    public SessionPropertiesResource(SessionPropertyWhitelist sessionPropertyWhitelist,
            SessionUtilsWrapper sessionUtilsWrapper, SessionResourceUtil sessionResourceUtil) {
        this.sessionPropertyWhitelist = sessionPropertyWhitelist;
        this.sessionUtilsWrapper = sessionUtilsWrapper;
        this.sessionResourceUtil = sessionResourceUtil;
    }

    /**
     * This endpoint does not support action.
     *
     * {@inheritDoc}
     */
    @Override
    public Promise<ActionResponse, ResourceException> actionInstance(Context context, ActionRequest actionRequest) {
        return RestUtils.generateUnsupportedOperation();
    }

    /**
     * Patch operation to selectively modify the session properties
     *
     * Implementation only supports OPERATION_REMOVE and OPERATION_REPLACE
     *
     * Use OPERATION_REMOVE to set the property to empty
     * Use OPERATION_REPLACE to update the value of a property
     *
     * @param context The context.
     * @param request The Request.
     * @return The response indicating the success or failure of the patch operation
     */
    @Override
    public Promise<ResourceResponse, ResourceException> patchInstance(Context context, PatchRequest request) {

        List<PatchOperation> operations = request.getPatchOperations();
        String tokenId;
        JsonValue result;
        try {
            ensurePatchPermitted(context, operations);
            tokenId = findTokenIdFromUri(context);
            SSOToken target = getToken(tokenId);
            for (PatchOperation operation : operations) {
                switch (operation.getOperation()) {
                    case OPERATION_REMOVE:
                        target.setProperty(getPatchProperty(operation), "");
                        break;
                    case OPERATION_REPLACE:
                        target.setProperty(getPatchProperty(operation), operation.getValue().asString());
                        break;
                }
            }
            result = getSessionProperties(context, tokenId);
        } catch (BadRequestException | ForbiddenException e) {
            return e.asPromise();
        } catch (SSOException  | IdRepoException e) {
            LOGGER.message("Unable to read session property due to unreadable SSOToken", e);
            return new BadRequestException().asPromise();
        }
        return newResultPromise(newResourceResponse(tokenId, String.valueOf(result.getObject().hashCode()), result));
    }


    /**
     *  This method returns the name value pairs of the session white listed properties if available.
     *
     * @param context The context.
     * @param request The request.
     * @return The name value pairs of the session properties.
     */
    @Override
    public Promise<ResourceResponse, ResourceException> readInstance(Context context, ReadRequest request) {
        String tokenId = findTokenIdFromUri(context);
        JsonValue result;
        try {
            result = getSessionProperties(context, tokenId);
        } catch (SSOException | IdRepoException e) {
            LOGGER.message("Unable to read session property due to unreadable SSOToken", e);
            return new BadRequestException().asPromise();
        }
        return newResultPromise(newResourceResponse(tokenId, String.valueOf(result.getObject().hashCode()), result));
    }

    /**
     *
     * The update modify the entire set of white listed properties,
     * Update wont be permitted if request does not encompass the name value pair of all the white listed properties
     *
     * @param context The context.
     * @param request The Request.
     * @return The response indicating the success or failure of the update operation
     */
    @Override
    public Promise<ResourceResponse, ResourceException> updateInstance(Context context, UpdateRequest request) {
        String tokenId;
        JsonValue result;
        try {
            tokenId = findTokenIdFromUri(context);
            SSOToken target = getToken(tokenId);
            JsonValue content = request.getContent();
            ensureUpdatePermitted(context, content, target);

            for (Map.Entry<String, String> entry : content.asMap(String.class).entrySet()) {
                target.setProperty(entry.getKey(), entry.getValue());
            }
            result = getSessionProperties(context, tokenId);
        } catch (BadRequestException | ForbiddenException e) {
            return e.asPromise();
        } catch (SSOException | IdRepoException e) {
            LOGGER.message("Unable to set session property due to unreadable SSOToken", e);
            return new BadRequestException().asPromise();
        } catch (DelegationException e) {
            LOGGER.message("Unable to read session property due to delegation match internal error", e);
            return new InternalServerErrorException().asPromise();
        }
        return newResultPromise(newResourceResponse(tokenId, String.valueOf(result.getObject().hashCode()), result));
    }

    private String findTokenIdFromUri(Context context) {
        if(context == null || context.asContext(UriRouterContext.class) == null) {
            return null;
        } else if(context.asContext(UriRouterContext.class).getUriTemplateVariables().get(TOKEN_ID_PARAM_NAME) == null) {
            return findTokenIdFromUri(context.getParent());
        }
        return context.asContext(UriRouterContext.class).getUriTemplateVariables().get(TOKEN_ID_PARAM_NAME);
    }

    private JsonValue getSessionProperties(Context context, String tokenId) throws SSOException, IdRepoException {
        JsonValue result = json(object());
        //String realm = context.asContext(RealmContext.class).getRealm().asPath();
        SSOToken target = getToken(tokenId);
        String realm = getTargetRealm(target);
        for (String property : sessionPropertyWhitelist.getAllListedProperties(realm)) {
            final String value = target.getProperty(property);
            result.add(property, value == null ? "" : value);
        }
        return result;
    }

    private SSOToken getToken(String tokenId) throws SSOException {
        return sessionResourceUtil.getTokenWithoutResettingIdleTime(tokenId);
    }

    private void ensurePatchPermitted(Context context, List<PatchOperation> operations)
            throws ForbiddenException, BadRequestException, SSOException {
        operationsAllowed(operations);
        propertiesModifiable(context, operations);
    }

    private void operationsAllowed(List<PatchOperation> operations) throws BadRequestException {
        for (PatchOperation operation : operations) {
            if (!SUPPORTED_OPERATIONS.contains(operation.getOperation())) {
                LOGGER.warning("Operation {} requested by the user is not allowed.", operation.getOperation());
                throw new BadRequestException();
            }
        }
    }

    private void propertiesModifiable(Context context, List<PatchOperation> operations)
            throws ForbiddenException, SSOException, BadRequestException {
        SSOToken caller = context.asContext(SSOTokenContext.class).getCallerSSOToken();
        for (PatchOperation operation : operations) {
            String property = getPatchProperty(operation);
            try {
                String value = operation.getValue().asString();
                sessionUtilsWrapper.checkPermissionToSetProperty(caller, property, value);
            }  catch (JsonValueException e) {
                LOGGER.warning("Operation {} requested by the user is not allowed.", operation.getOperation());
                throw new BadRequestException();
            } catch (SessionException e) {
                LOGGER.warning("User {} requested patch a property {} which was not whitelisted.",
                        caller.getPrincipal(), property);
                throw new ForbiddenException();
            }
        }
    }

    /**
     * Ensures the update is permitted by checking the request has valid contents.
     * Update is permitted only if the request contains all the white listed properties
     *
     * @param context The context,
     * @param content The request content.
     * @param target  The target session SSOToken
     * @throws SSOException When the SSOToken in invalid
     * @throws BadRequestException When the request has not content
     * @throws DelegationException When is whitelisted check fails
     * @throws ForbiddenException When the content in the request does not match whitelisted properties
     */
    private void ensureUpdatePermitted(Context context, JsonValue content, SSOToken target) throws SSOException,
            BadRequestException, DelegationException, ForbiddenException, IdRepoException {

        SSOToken caller = context.asContext(SSOTokenContext.class).getCallerSSOToken();
        //String realm = context.asContext(RealmContext.class).getRealm().asPath();
        String realm = getTargetRealm(target);

        try {
            if (content == null || content.isNull() || content.asMap(String.class).size() == 0) {
                LOGGER.warning("User {} requested with an empty values.", caller.getPrincipal());
                throw new BadRequestException();
            }
        } catch (JsonValueException e) {
            //exception is content can't be accessed as a map
            LOGGER.warning("User {} requested with no property value pairs", caller.getPrincipal());
            throw new BadRequestException();
        }

        Map<String, String> entrySet = content.asMap(String.class);
        if (!sessionPropertyWhitelist.getAllListedProperties(realm).equals(entrySet.keySet())
                || !sessionPropertyWhitelist.isPropertyMapSettable(caller, entrySet)) {
            LOGGER.warning("User {} requested property/ies {} to set on {} which was not whitelisted.",
                    caller.getPrincipal(), target.getPrincipal(), entrySet.toString());
            throw new ForbiddenException();
        }
    }

    private String getTargetRealm(SSOToken ssoToken) throws IdRepoException, SSOException {
        return sessionResourceUtil.convertDNToRealm( sessionResourceUtil.getIdentity(ssoToken).getRealm());
    }

    private String getPatchProperty(PatchOperation operation) {
        String result = operation.getField().toString();
        String prefix = "/";
        if (result.startsWith(prefix)) {
            result = result.substring(prefix.length());
        }
        return result;
    }
}
