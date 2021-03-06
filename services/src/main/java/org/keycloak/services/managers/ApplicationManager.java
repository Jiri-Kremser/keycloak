package org.keycloak.services.managers;

import org.jboss.resteasy.logging.Logger;
import org.keycloak.models.ApplicationModel;
import org.keycloak.models.Constants;
import org.keycloak.models.RealmModel;
import org.keycloak.models.RoleModel;
import org.keycloak.models.UserCredentialModel;
import org.keycloak.models.UserModel;
import org.keycloak.representations.adapters.config.BaseAdapterConfig;
import org.keycloak.representations.idm.ApplicationRepresentation;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.ScopeMappingRepresentation;
import org.keycloak.representations.idm.UserRoleMappingRepresentation;
import org.keycloak.services.resources.flows.Urls;

import java.net.URI;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.HashMap;

/**
 * @author <a href="mailto:bill@burkecentral.com">Bill Burke</a>
 * @version $Revision: 1 $
 */
public class ApplicationManager {
    protected Logger logger = Logger.getLogger(ApplicationManager.class);

    protected RealmManager realmManager;

    public ApplicationManager(RealmManager realmManager) {
        this.realmManager = realmManager;
    }

    /**
     * Does not create scope or role mappings!
     *
     * @param realm
     * @param loginRole
     * @param resourceRep
     * @return
     */
    public ApplicationModel createApplication(RealmModel realm, RoleModel loginRole, ApplicationRepresentation resourceRep) {
        logger.debug("************ CREATE APPLICATION: {0}" + resourceRep.getName());
        ApplicationModel applicationModel = realm.addApplication(resourceRep.getName());
        applicationModel.setEnabled(resourceRep.isEnabled());
        applicationModel.setManagementUrl(resourceRep.getAdminUrl());
        applicationModel.setSurrogateAuthRequired(resourceRep.isSurrogateAuthRequired());
        applicationModel.setBaseUrl(resourceRep.getBaseUrl());
        applicationModel.updateApplication();

        UserModel resourceUser = applicationModel.getApplicationUser();
        if (resourceRep.getCredentials() != null) {
            for (CredentialRepresentation cred : resourceRep.getCredentials()) {
                UserCredentialModel credential = new UserCredentialModel();
                credential.setType(cred.getType());
                credential.setValue(cred.getValue());
                realm.updateCredential(resourceUser, credential);
            }
        }
        if (resourceRep.getRedirectUris() != null) {
            for (String redirectUri : resourceRep.getRedirectUris()) {
                resourceUser.addRedirectUri(redirectUri);
            }
        }
        if (resourceRep.getWebOrigins() != null) {
            for (String webOrigin : resourceRep.getWebOrigins()) {
                logger.debug("Application: {0} webOrigin: {1}", resourceUser.getLoginName(), webOrigin);
                resourceUser.addWebOrigin(webOrigin);
            }
        }

        realm.grantRole(resourceUser, loginRole);


        if (resourceRep.getRoles() != null) {
            for (RoleRepresentation roleRep : resourceRep.getRoles()) {
                RoleModel role = applicationModel.addRole(roleRep.getName());
                if (roleRep.getDescription() != null) role.setDescription(roleRep.getDescription());
            }
        }

        if (resourceRep.getDefaultRoles() != null) {
            applicationModel.updateDefaultRoles(resourceRep.getDefaultRoles());
        }

        return applicationModel;
    }

    public void createRoleMappings(RealmModel realm, ApplicationModel applicationModel, List<UserRoleMappingRepresentation> mappings) {
        for (UserRoleMappingRepresentation mapping : mappings) {
            UserModel user = realm.getUser(mapping.getUsername());
            if (user == null) {
                throw new RuntimeException("User not found");
            }
            for (String roleString : mapping.getRoles()) {
                RoleModel role = applicationModel.getRole(roleString.trim());
                if (role == null) {
                    role = applicationModel.addRole(roleString.trim());
                }
                applicationModel.grantRole(user, role);
            }
        }
    }

    public void createScopeMappings(RealmModel realm, ApplicationModel applicationModel, List<ScopeMappingRepresentation> mappings) {
        for (ScopeMappingRepresentation mapping : mappings) {
            UserModel user = realm.getUser(mapping.getUsername());
            for (String roleString : mapping.getRoles()) {
                RoleModel role = applicationModel.getRole(roleString.trim());
                if (role == null) {
                    role = applicationModel.addRole(roleString.trim());
                }
                applicationModel.addScopeMapping(user, role.getName());
            }
        }
    }

    public ApplicationModel createApplication(RealmModel realm, ApplicationRepresentation resourceRep) {
        RoleModel loginRole = realm.getRole(Constants.APPLICATION_ROLE);
        return createApplication(realm, loginRole, resourceRep);
    }

    public void updateApplication(ApplicationRepresentation rep, ApplicationModel resource) {
        resource.setName(rep.getName());
        resource.setEnabled(rep.isEnabled());
        resource.setManagementUrl(rep.getAdminUrl());
        resource.setBaseUrl(rep.getBaseUrl());
        resource.setSurrogateAuthRequired(rep.isSurrogateAuthRequired());
        resource.updateApplication();

        if (rep.getDefaultRoles() != null) {
            resource.updateDefaultRoles(rep.getDefaultRoles());
        }

        List<String> redirectUris = rep.getRedirectUris();
        if (redirectUris != null) {
            resource.getApplicationUser().setRedirectUris(new HashSet<String>(redirectUris));
        }

        List<String> webOrigins = rep.getWebOrigins();
        if (webOrigins != null) {
            resource.getApplicationUser().setWebOrigins(new HashSet<String>(webOrigins));
        }
    }

    public ApplicationRepresentation toRepresentation(ApplicationModel applicationModel) {
        ApplicationRepresentation rep = new ApplicationRepresentation();
        rep.setId(applicationModel.getId());
        rep.setName(applicationModel.getName());
        rep.setEnabled(applicationModel.isEnabled());
        rep.setAdminUrl(applicationModel.getManagementUrl());
        rep.setSurrogateAuthRequired(applicationModel.isSurrogateAuthRequired());
        rep.setBaseUrl(applicationModel.getBaseUrl());

        Set<String> redirectUris = applicationModel.getApplicationUser().getRedirectUris();
        if (redirectUris != null) {
            rep.setRedirectUris(new LinkedList<String>(redirectUris));
        }

        Set<String> webOrigins = applicationModel.getApplicationUser().getWebOrigins();
        if (webOrigins != null) {
            rep.setWebOrigins(new LinkedList<String>(webOrigins));
        }

        if (!applicationModel.getDefaultRoles().isEmpty()) {
            rep.setDefaultRoles(applicationModel.getDefaultRoles().toArray(new String[0]));
        }

        return rep;

    }

    public BaseAdapterConfig toInstallationRepresentation(RealmModel realmModel, ApplicationModel applicationModel, URI baseUri) {
        BaseAdapterConfig rep = new BaseAdapterConfig();
        rep.setRealm(realmModel.getName());
        rep.setRealmKey(realmModel.getPublicKeyPem());
        rep.setSslNotRequired(realmModel.isSslNotRequired());

        rep.setAuthServerUrl(baseUri.toString());
        rep.setUseResourceRoleMappings(applicationModel.getRoles().size() > 0);

        rep.setResource(applicationModel.getName());

        Map<String, String> creds = new HashMap<String, String>();
        creds.put(CredentialRepresentation.PASSWORD, "INSERT APPLICATION PASSWORD");
        if (applicationModel.getApplicationUser().isTotp()) {
            creds.put(CredentialRepresentation.TOTP, "INSERT APPLICATION TOTP");
        }
        rep.setCredentials(creds);

        return rep;
    }
}
