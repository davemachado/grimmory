package org.booklore.service.appsettings;

import org.booklore.config.AppProperties;
import org.booklore.config.security.service.AuthenticationService;
import org.booklore.model.dto.BookLoreUser;
import org.booklore.model.dto.settings.AppSettingKey;
import org.booklore.model.entity.AppSettingEntity;
import org.booklore.model.enums.AuditAction;
import org.booklore.repository.AppSettingsRepository;
import org.booklore.service.audit.AuditService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppSettingServiceTest {

    @Mock
    private AppProperties appProperties;
    @Mock
    private AuthenticationService authenticationService;
    @Mock
    private AuditService auditService;
    @Mock
    private AppSettingsRepository appSettingsRepository;

    private SettingPersistenceHelper settingPersistenceHelper;

    private AppSettingService appSettingService;

    @BeforeEach
    void setUp() {
        settingPersistenceHelper = new SettingPersistenceHelper(appSettingsRepository, new ObjectMapper());
        appSettingService = new AppSettingService(appProperties, settingPersistenceHelper, authenticationService, auditService);

        var permissions = new BookLoreUser.UserPermissions();
        permissions.setAdmin(true);
        BookLoreUser user = BookLoreUser.builder()
                .id(1L)
                .username("admin")
                .permissions(permissions)
                .build();

        when(authenticationService.getAuthenticatedUser()).thenReturn(user);
    }

    @Test
    void updateSetting_keepsStoredClientSecretWhenIncomingSecretIsBlank() throws Exception {
        AppSettingEntity stored = new AppSettingEntity();
        stored.setName(AppSettingKey.OIDC_PROVIDER_DETAILS.toString());
        stored.setVal("{\"clientId\":\"grimmory\",\"clientSecret\":\"super-secret\"}");
        when(appSettingsRepository.findByName(AppSettingKey.OIDC_PROVIDER_DETAILS.toString())).thenReturn(stored);

        appSettingService.updateSetting(
                AppSettingKey.OIDC_PROVIDER_DETAILS,
                Map.of("clientId", "grimmory", "clientSecret", "")
        );

        ArgumentCaptor<AppSettingEntity> settingCaptor = ArgumentCaptor.forClass(AppSettingEntity.class);
        verify(appSettingsRepository).save(settingCaptor.capture());
        assertThat(settingCaptor.getValue().getVal()).contains("\"clientSecret\":\"super-secret\"");
    }

    @Test
    void updateSetting_keepsStoredClientSecretWhenIncomingSecretIsMissing() throws Exception {
        AppSettingEntity stored = new AppSettingEntity();
        stored.setName(AppSettingKey.OIDC_PROVIDER_DETAILS.toString());
        stored.setVal("{\"clientId\":\"grimmory\",\"clientSecret\":\"super-secret\"}");
        when(appSettingsRepository.findByName(AppSettingKey.OIDC_PROVIDER_DETAILS.toString())).thenReturn(stored);

        appSettingService.updateSetting(
                AppSettingKey.OIDC_PROVIDER_DETAILS,
                Map.of("clientId", "grimmory")
        );

        ArgumentCaptor<AppSettingEntity> settingCaptor = ArgumentCaptor.forClass(AppSettingEntity.class);
        verify(appSettingsRepository).save(settingCaptor.capture());
        assertThat(settingCaptor.getValue().getVal()).contains("\"clientSecret\":\"super-secret\"");
    }

    @Test
    void updateSetting_keepsStoredClientSecretWhenIncomingSecretIsNull() throws Exception {
        AppSettingEntity stored = new AppSettingEntity();
        stored.setName(AppSettingKey.OIDC_PROVIDER_DETAILS.toString());
        stored.setVal("{\"clientId\":\"grimmory\",\"clientSecret\":\"super-secret\"}");
        when(appSettingsRepository.findByName(AppSettingKey.OIDC_PROVIDER_DETAILS.toString())).thenReturn(stored);

        Map<String, Object> incoming = new HashMap<>();
        incoming.put("clientId", "grimmory");
        incoming.put("clientSecret", null);

        appSettingService.updateSetting(AppSettingKey.OIDC_PROVIDER_DETAILS, incoming);

        ArgumentCaptor<AppSettingEntity> settingCaptor = ArgumentCaptor.forClass(AppSettingEntity.class);
        verify(appSettingsRepository).save(settingCaptor.capture());
        assertThat(settingCaptor.getValue().getVal()).contains("\"clientSecret\":\"super-secret\"");
    }

    @Test
    void updateSetting_updatesClientSecretWhenNewSecretProvided() throws Exception {
        AppSettingEntity stored = new AppSettingEntity();
        stored.setName(AppSettingKey.OIDC_PROVIDER_DETAILS.toString());
        stored.setVal("{\"clientId\":\"grimmory\",\"clientSecret\":\"old-secret\"}");
        when(appSettingsRepository.findByName(AppSettingKey.OIDC_PROVIDER_DETAILS.toString())).thenReturn(stored);

        appSettingService.updateSetting(
                AppSettingKey.OIDC_PROVIDER_DETAILS,
                Map.of("clientId", "grimmory", "clientSecret", "new-secret")
        );

        ArgumentCaptor<AppSettingEntity> settingCaptor = ArgumentCaptor.forClass(AppSettingEntity.class);
        verify(appSettingsRepository).save(settingCaptor.capture());
        assertThat(settingCaptor.getValue().getVal()).contains("\"clientSecret\":\"new-secret\"");
    }

    @Test
    void updateSetting_acceptsValidOidcRedirectUris() throws Exception {
        appSettingService.updateSetting(
                AppSettingKey.OIDC_REDIRECT_URIS,
                List.of("grimmory://oauth2-callback", "grimmory://auth/return")
        );

        ArgumentCaptor<AppSettingEntity> settingCaptor = ArgumentCaptor.forClass(AppSettingEntity.class);
        verify(appSettingsRepository).save(settingCaptor.capture());

        AppSettingEntity savedSetting = settingCaptor.getValue();
        assertThat(savedSetting.getName()).isEqualTo(AppSettingKey.OIDC_REDIRECT_URIS.toString());
        assertThat(savedSetting.getVal()).isEqualTo("[\"grimmory://oauth2-callback\",\"grimmory://auth/return\"]");
        verify(auditService).log(AuditAction.OIDC_CONFIG_CHANGED, "Updated setting: " + AppSettingKey.OIDC_REDIRECT_URIS);
    }

    @Test
    void updateSetting_acceptsWildcardOidcRedirectUri() throws Exception {
        appSettingService.updateSetting(
                AppSettingKey.OIDC_REDIRECT_URIS,
                List.of("*")
        );

        ArgumentCaptor<AppSettingEntity> settingCaptor = ArgumentCaptor.forClass(AppSettingEntity.class);
        verify(appSettingsRepository).save(settingCaptor.capture());

        AppSettingEntity savedSetting = settingCaptor.getValue();
        assertThat(savedSetting.getName()).isEqualTo(AppSettingKey.OIDC_REDIRECT_URIS.toString());
        assertThat(savedSetting.getVal()).isEqualTo("[\"*\"]");
        verify(auditService).log(AuditAction.OIDC_CONFIG_CHANGED, "Updated setting: " + AppSettingKey.OIDC_REDIRECT_URIS);
    }

    @Test
    void updateSetting_rejectsWildcardCombinedWithOtherUris() {
        assertThatThrownBy(() -> appSettingService.updateSetting(
                AppSettingKey.OIDC_REDIRECT_URIS,
                List.of("*", "grimmory://oauth2-callback")
        ))
                .hasMessageContaining("Wildcard redirect URI must be the only value");

        verify(appSettingsRepository, never()).save(any());
    }

    @Test
    void updateSetting_rejectsBlankOidcMobileRedirectUri() {
        assertThatThrownBy(() -> appSettingService.updateSetting(
                AppSettingKey.OIDC_REDIRECT_URIS,
                List.of(" ")
        ))
                .hasMessageContaining("Redirect URI cannot be blank");

        verify(appSettingsRepository, never()).save(any());
    }

    @Test
    void updateSetting_rejectsNonStringOidcMobileRedirectUriEntries() {
        assertThatThrownBy(() -> appSettingService.updateSetting(
                AppSettingKey.OIDC_REDIRECT_URIS,
                List.of(42)
        ))
                .hasMessageContaining("OIDC redirect URIs must be an array of strings");

        verify(appSettingsRepository, never()).save(any());
    }

    @Test
    void updateSetting_rejectsDuplicateOidcMobileRedirectUris() {
        assertThatThrownBy(() -> appSettingService.updateSetting(
                AppSettingKey.OIDC_REDIRECT_URIS,
                List.of("grimmory://oauth2-callback", "grimmory://oauth2-callback")
        ))
                .hasMessageContaining("Duplicate redirect URI");

        verify(appSettingsRepository, never()).save(any());
    }

    @Test
    void updateSetting_rejectsHttpRedirectUri() {
        assertThatThrownBy(() -> appSettingService.updateSetting(
                AppSettingKey.OIDC_REDIRECT_URIS,
                List.of("https://example.com/oauth2-callback")
        ))
                .hasMessageContaining("Redirect URI must use a custom mobile scheme");

        verify(appSettingsRepository, never()).save(any());
    }

    @Test
    void updateSetting_rejectsRedirectUriWithFragment() {
        assertThatThrownBy(() -> appSettingService.updateSetting(
                AppSettingKey.OIDC_REDIRECT_URIS,
                List.of("grimmory://oauth2-callback#done")
        ))
                .hasMessageContaining("Redirect URI must not contain a fragment");

        verify(appSettingsRepository, never()).save(any());
    }

    @Test
    void updateSetting_rejectsRedirectUriWithoutScheme() {
        assertThatThrownBy(() -> appSettingService.updateSetting(
                AppSettingKey.OIDC_REDIRECT_URIS,
                List.of("oauth2-callback")
        ))
                .hasMessageContaining("Redirect URI must include a scheme");

        verify(appSettingsRepository, never()).save(any());
    }
}
