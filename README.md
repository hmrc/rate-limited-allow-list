
# rate-limited-allow-list

This service is intended to reduce the amount of code service teams need to write, maintain and later remove for managing a gradual rollout of a service or feature to a group of users (for example, as part of a private beta rollout). It is responsible for storing a list of the user's identifiers and the rollout limits, and allow the service can check against that  list.

## How does it work

The service allows you to specify the number of users you want to allow, i.e. the rate, by setting the token limit. A token is consumed if the identifier is not on already on the list and there are still tokens available for your service + feature, the user will be added to this feature for your service. If there are no tokens remaining and the users is not on the list you will get a negative response that you can use in determining where to route the user. 

You can manage the numbers of tokens over time - add, remove, to control the rate at which users are onboarded. See the section below on [#managing-tokens-for-your-service-and-feature].

## What it is not

- It is not a replacement for other forms of authentication or authorisation. You should still make use of other predicates to ensure a user is allowed to use your service.
- It is not for storing values that need to be retrieved - it only allows you to check if a value is present. You cannot retrieve the original values back.
- It is not for long-term storage. The production TTL is set to 30 days since last update.
- It is not intended for "feature flagging" functionality in your service.
- It does not allow multiple identifiers to be checked in a single request. It is intended for single lookups.
- It does not allow "partial matches". Identifiers must match exactly.
- It cannot manage lists that are shared across multiple services. Each service can only access its own identifier list.

## Integrating with user-allow-list

### 1. Decide on the identifier and feature name

Before starting any implementation you should decide upon a stable identifier you will use to identify users. For example, a national insurance number or other identifier retrieved from auth. _This cannot be a value provided by a user themselves_.

You should also decide upon a feature name, which is used to identify a specific list. For example for use in a service named `fake-frontend` for a private beta, you can use the feature name `fake-frontend-private-beta-2026`.

### 2. Initialise the number of tokens for you service 

In `app-config-<env>`, add your service to the list of other configs in `mongodb.collection.allow-list-metadata.token-updates` (note: this is and array of config values).  See the section on using the config to set the number of tokens.

```yaml
mongodb.collections.allow-list-metadata.token-updates.0.service: 'fake-frontend'
mongodb.collections.allow-list-metadata.token-updates.0.feature: 'private-beta-2026'
mongodb.collections.allow-list-metadata.token-updates.0.maxUsers: 100
```

## Managing tokens for your service and feature

To manage tokens there are 2 options, (1) using the admin service `rate-limited-allow-list-admin-fronted`, (2) updating the config and re-deploying the application. The config driven approach is a fallback that will only be used when the admin service is not available.

### Using the config to set the maximum number of users

- Maximum number of users can be managed by updating the configuration and this will be applied on application start. 
- `features.allow-config-token-updates` must be `enabled`
- The structure of the config for you service is seen below.

```
mongodb {
    collections {
        allow-list-metadata {
            token-updates = [
                {
                  service = 'fake-frontend'
                  feature = 'private-beta-2026'
                  maxUsers = 100
                }
            ]
        }
    }
}
```

- The service must be the name of the calling service
- The feature is the value you picked
- `maxUser` must be set to a value > 0. On deployment this value will be used to update the current token value. For example if there are 10 tokens left and there are 20 users, after this config change is applied, the token value will be 70.

### License

This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html").