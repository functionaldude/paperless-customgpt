CREATE TABLE IF NOT EXISTS oauth2_registered_client
(
    id                            varchar(100)  NOT NULL,
    client_id                     varchar(100)  NOT NULL,
    client_id_issued_at           timestamp     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    client_secret                 varchar(200)           DEFAULT NULL,
    client_secret_expires_at      timestamp              DEFAULT NULL,
    client_name                   varchar(200)  NOT NULL,
    client_authentication_methods varchar(1000) NOT NULL,
    authorization_grant_types     varchar(1000) NOT NULL,
    redirect_uris                 varchar(1000)          DEFAULT NULL,
    post_logout_redirect_uris     varchar(1000)          DEFAULT NULL,
    scopes                        varchar(1000) NOT NULL,
    client_settings               varchar(2000) NOT NULL,
    token_settings                varchar(2000) NOT NULL,
    PRIMARY KEY (id)
);

CREATE UNIQUE INDEX IF NOT EXISTS oauth2_registered_client_client_id_idx
    ON oauth2_registered_client (client_id);

CREATE TABLE IF NOT EXISTS oauth2_authorization
(
    id                            varchar(100) NOT NULL,
    registered_client_id          varchar(100) NOT NULL,
    principal_name                varchar(200) NOT NULL,
    authorization_grant_type      varchar(100) NOT NULL,
    authorized_scopes             varchar(1000) DEFAULT NULL,
    attributes                    text          DEFAULT NULL,
    state                         varchar(500)  DEFAULT NULL,

    authorization_code_value      bytea         DEFAULT NULL,
    authorization_code_issued_at  timestamp     DEFAULT NULL,
    authorization_code_expires_at timestamp     DEFAULT NULL,
    authorization_code_metadata   text          DEFAULT NULL,

    access_token_value            bytea         DEFAULT NULL,
    access_token_issued_at        timestamp     DEFAULT NULL,
    access_token_expires_at       timestamp     DEFAULT NULL,
    access_token_metadata         text          DEFAULT NULL,
    access_token_type             varchar(100)  DEFAULT NULL,
    access_token_scopes           varchar(1000) DEFAULT NULL,

    oidc_id_token_value           bytea         DEFAULT NULL,
    oidc_id_token_issued_at       timestamp     DEFAULT NULL,
    oidc_id_token_expires_at      timestamp     DEFAULT NULL,
    oidc_id_token_metadata        text          DEFAULT NULL,

    refresh_token_value           bytea         DEFAULT NULL,
    refresh_token_issued_at       timestamp     DEFAULT NULL,
    refresh_token_expires_at      timestamp     DEFAULT NULL,
    refresh_token_metadata        text          DEFAULT NULL,

    user_code_value               bytea         DEFAULT NULL,
    user_code_issued_at           timestamp     DEFAULT NULL,
    user_code_expires_at          timestamp     DEFAULT NULL,
    user_code_metadata            text          DEFAULT NULL,

    device_code_value             bytea         DEFAULT NULL,
    device_code_issued_at         timestamp     DEFAULT NULL,
    device_code_expires_at        timestamp     DEFAULT NULL,
    device_code_metadata          text          DEFAULT NULL,

    PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS oauth2_authorization_registered_client_id_idx
    ON oauth2_authorization (registered_client_id);

CREATE INDEX IF NOT EXISTS oauth2_authorization_principal_name_idx
    ON oauth2_authorization (principal_name);

CREATE INDEX IF NOT EXISTS oauth2_authorization_state_idx
    ON oauth2_authorization (state);

CREATE INDEX IF NOT EXISTS oauth2_authorization_authorization_code_value_idx
    ON oauth2_authorization (authorization_code_value);

CREATE INDEX IF NOT EXISTS oauth2_authorization_access_token_value_idx
    ON oauth2_authorization (access_token_value);

CREATE INDEX IF NOT EXISTS oauth2_authorization_oidc_id_token_value_idx
    ON oauth2_authorization (oidc_id_token_value);

CREATE INDEX IF NOT EXISTS oauth2_authorization_refresh_token_value_idx
    ON oauth2_authorization (refresh_token_value);

CREATE INDEX IF NOT EXISTS oauth2_authorization_user_code_value_idx
    ON oauth2_authorization (user_code_value);

CREATE INDEX IF NOT EXISTS oauth2_authorization_device_code_value_idx
    ON oauth2_authorization (device_code_value);

CREATE TABLE IF NOT EXISTS oauth2_authorization_consent
(
    registered_client_id varchar(100)  NOT NULL,
    principal_name       varchar(200)  NOT NULL,
    authorities          varchar(1000) NOT NULL,
    PRIMARY KEY (registered_client_id, principal_name)
);
