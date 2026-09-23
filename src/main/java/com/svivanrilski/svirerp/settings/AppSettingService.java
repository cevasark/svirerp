package com.svivanrilski.svirerp.settings;

import com.svivanrilski.svirerp.common.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class AppSettingService {

    private static final String SECRET_TYPE = "SECRET";

    private final AppSettingRepository repo;
    private final SettingEncryptor encryptor;

    public List<AppSetting> list() {
        return repo.findAll();
    }

    /**
     * Decrypts SECRET values transparently; returns the raw value for
     * everything else. This is what the dynamic Google ClientRegistrationRepository
     * calls on every OAuth2 login attempt.
     */
    public Optional<String> getDecryptedValue(String key) {
        return repo.findByKey(key).flatMap(setting -> {
            String value = setting.getValue();
            if (value == null || value.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(isSecretType(setting) ? encryptor.decrypt(value) : value);
        });
    }

    /** Reports whether a setting has a value without decrypting or exposing a secret. */
    public boolean hasValue(String key) {
        return repo.findByKey(key)
                .map(AppSetting::getValue)
                .filter(value -> !value.isBlank())
                .isPresent();
    }

    @Transactional
    public AppSetting updateValue(String key, String newValue) {
        AppSetting setting = repo.findByKey(key)
                .orElseThrow(() -> new ResourceNotFoundException("Setting not found: " + key));
        boolean isSecret = isSecretType(setting);
        setting.setValue(isSecret && newValue != null && !newValue.isBlank() ? encryptor.encrypt(newValue) : newValue);
        return repo.save(setting);
    }

    private boolean isSecretType(AppSetting setting) {
        return SECRET_TYPE.equals(setting.getValueType());
    }
}
