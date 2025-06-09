# Axiom: SCIM 2.0 to Keycloak Bridge

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Java Version](https://img.shields.io/badge/Java-17-blue.svg)](https://www.java.com)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.0-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Keycloak](https://img.shields.io/badge/Keycloak-26.1.4-blue.svg)](https://www.keycloak.org/)

**Axiom** is a robust, production-ready service that acts as a bridge between the **SCIM 2.0 protocol** and the **Keycloak Admin API**. It exposes a standard, compliant SCIM 2.0 interface, allowing any SCIM-compatible Identity Provider (IdP) or client to manage users and groups in a Keycloak instance seamlessly.

---

## 🌟 Features

-   **SCIM 2.0 Compliance**: Implements core SCIM 2.0 resources and operations.
    -   **Users**: Create, Read (Get), Update (PUT/PATCH), and Delete.
    -   **Groups**: Create, Read, Update (PUT/PATCH), and Delete, including member management.
    -   **Discovery**: `ServiceProviderConfig`, `ResourceTypes`, and `Schemas` endpoints are fully implemented.
-   **Secure by Design**: Endpoints are secured and support authentication via pre-configured static Bearer Tokens (ideal for IdPs with limited OAuth2 client capabilities). The internal Keycloak Admin Client communication remains secured by standard OAuth 2.0 JWTs
-   **Robust & Scalable**: Built with Spring Boot for high performance and easy containerization.
-   **Enterprise-Ready**: Maps standard SCIM attributes and the Enterprise User extension to Keycloak user attributes.
-   **Extensible**: Cleanly structured with mappers and services, making it easy to add custom attribute mappings.

## 🏛️ Architecture

The application follows a classic three-tier architecture within a Dockerized environment, orchestrated by Docker Compose and fronted by an NGINX reverse proxy.

1.  **NGINX Reverse Proxy**: Handles incoming traffic, terminates SSL/TLS, and routes requests to the appropriate service based on the domain name. It ensures both Keycloak and the Axiom bridge are accessible via standard HTTPS.
2.  **Axiom (SCIM Bridge)**: The core Spring Boot application that translates SCIM requests to Keycloak API calls.
3.  **Keycloak**: The Identity and Access Management system, responsible for authentication and storing user/group data.
4.  **MariaDB**: The persistent database backend for Keycloak.


## 🚀 Getting Started

These instructions will guide you through setting up the complete environment on a fresh server (e.g., an AWS EC2 instance).

### Prerequisites

-   A Linux server (Ubuntu 22.04 LTS is recommended).
-   Two registered domain names (e.g., `keycloak.yourdomain.com` and `scim.yourdomain.com`).
-   DNS `A` records for both domains pointing to your server's public IP address.
-   **Docker** and **Docker Compose** installed.
    ```bash
    # Install Docker
    sudo apt-get update
    sudo apt-get install -y docker.io
    sudo systemctl start docker
    sudo systemctl enable docker
    sudo usermod -aG docker $USER
    newgrp docker # Activate group change without needing to log out/in
    
    # Install Docker Compose
    sudo apt-get install -y docker-compose-v2
    ```
-   **NGINX** and **Certbot** installed.
    ```bash
    sudo apt-get install -y nginx certbot python3-certbot-nginx
    ```

### 1. Clone the Repository

Clone the project to your server. The project includes the Axiom source code and the `docker-compose.yml` file.


### 2. Configure Environment Variables

Update the `application.yml` file.

```bash
  security:
    static-tokens:
      # Add one or more secret, long, and random tokens here
      - "REPLACE-WITH-YOUR-FIRST-SUPER-SECRET-STATIC-TOKEN"
      - "REPLACE-WITH-ANOTHER-OPTIONAL-STATIC-TOKEN"
```


Create a .env file in the root directory of the cloned project. This file will store all your sensitive and environment-specific configurations.

```bash
nano .env
```

Paste the following content into the file, adjusting the values to match your setup.

```bash
# Database Credentials
MARIADB_ROOT_PASSWORD=your_strong_mariadb_root_password
MARIADB_DATABASE=keycloak
MARIADB_USER=keycloak
MARIADB_PASSWORD=your_strong_keycloak_db_password

# Keycloak Admin Credentials & Host
KEYCLOAK_ADMIN_USER=admin
KEYCLOAK_ADMIN_PASSWORD=your_strong_keycloak_admin_password
KC_HOSTNAME_URL=https://keycloak.yourdomain.com # Public URL for Keycloak
KEYCLOAK_HOST_HTTP_PORT=8081 # Host port Keycloak's HTTP will be mapped to

# Axiom (SCIM Bridge) Host
SCIM_BRIDGE_HOST_PORT=8082 # Host port Axiom's HTTP will be mapped to

# Axiom Configuration (Keycloak Admin Client & Security)
KEYCLOAK_BRIDGE_SECURED_BY_REALM=master
KEYCLOAK_ADMIN_CLIENT_REALM=master
KEYCLOAK_PROVISIONING_TARGET_REALM=master # Realm where users/groups will be provisioned by Axiom
KEYCLOAK_ADMIN_CLIENT_ID=scim-bridge-client # Client ID for Axiom to talk to Keycloak Admin API
KEYCLOAK_ADMIN_CLIENT_SECRET=REPLACE_WITH_A_VERY_STRONG_SECRET # Client Secret for scim-bridge-client (Keycloak Admin API)
```


### 3. Set Up NGINX Reverse Proxy & SSL

We will create NGINX server blocks to route traffic to our Docker containers and use Certbot to secure them with free, trusted SSL certificates from Let's Encrypt.

### a. Create NGINX Configuration Files

Create a configuration file for Keycloak:


```bash
sudo nano /etc/nginx/sites-available/keycloak.yourdomain.com
```

Paste this configuration (replace keycloak.yourdomain.com with your actual domain, and replace yourip with your actual instance ip):

```nginx
server {
        listen 80;
        server_name keycloak.yourdomain.com www.keycloak.yourdomain.com;
        rewrite ^https://keycloak.yourdomain.com permanent;
}
server {
        listen 443 ssl;
        server_name keycloak.yourdomain.com www.keycloak.yourdomain.com;

        ssl_certificate /etc/letsencrypt/live/keycloak.yourdomain.com/fullchain.pem;
        ssl_certificate_key /etc/letsencrypt/live/keycloak.yourdomain.com/privkey.pem;
        ssl_session_cache builtin:1000 shared:ssl:10m;
        ssl_protocols TLSv1 TLSv1.1 TLSv1.2 TLSv1.3;
        ssl_ciphers HIGH:!aNULL:!eNULL:!EXPORT:!CAMELLIA:!DES:!MD5:!PSK:!RC4;
        ssl_prefer_server_ciphers on;
        location / {
                proxy_set_header        Host                    $host;
                proxy_set_header        X-Real-IP               $remote_addr;
                proxy_set_header        X-Forwarded-For         $proxy_add_x_forwarded_for;
                proxy_set_header        X-Forwarded-Proto       $scheme;
                proxy_pass http://yourip:8081;
        }
}
```  

Create a configuration file for the SCIM bridge:
```bash
sudo nano /etc/nginx/sites-available/scim.yourdomain.com
```

Paste this configuration (replace scim.yourdomain.com with your actual domain):
```nginx
server {
    listen 80;
    server_name scim.yourdomain.com;

    location / {
        return 301 https://$host$request_uri;
    }
}

server {
    listen 443 ssl http2;
    server_name scim.yourdomain.com;

    ssl_certificate /etc/letsencrypt/live/scim.yourdomain.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/scim.yourdomain.com/privkey.pem;

    ssl_session_timeout 1d;
    ssl_session_cache shared:SSL:10m;
    ssl_session_tickets off;

    ssl_protocols TLSv1.2 TLSv1.3;
    ssl_prefer_server_ciphers off;

    location / {
        proxy_set_header        Host $host;
        proxy_set_header        X-Real-IP $remote_addr;
        proxy_set_header        X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header        X-Forwarded-Proto $scheme;
        proxy_set_header        X-Forwarded-Host $host;
        proxy_set_header        X-Forwarded-Port $server_port;

        # Your scim-bridge Docker container is mapped to host port 8082
        proxy_pass              http://localhost:8082;
    }

    access_log /var/log/nginx/scim.yourdomain.com.access.log;
    error_log /var/log/nginx/scim.yourdomain.com.error.log;
}
```

### b. Enable the Sites

Enable the NGINX site configurations and restart NGINX:

```bash
sudo ln -s /etc/nginx/sites-available/keycloak.yourdomain.com /etc/nginx/sites-enabled/
sudo ln -s /etc/nginx/sites-available/scim.yourdomain.com /etc/nginx/sites-enabled/
sudo nginx -t
sudo systemctl restart nginx
```

---

### c. Obtain SSL Certificates

Use **Certbot** to obtain SSL certificates and automatically configure HTTPS:

```bash
sudo certbot --nginx -d keycloak.yourdomain.com -d scim.yourdomain.com
```

- Follow the prompts.
- Choose the option to redirect HTTP to HTTPS.
- Certbot will automatically modify the NGINX configuration to enable SSL.

---

### d. Test and restart NGINX:

```bash
sudo nginx -t
sudo systemctl restart nginx
```

---

### 4. Build and Run the Application Stack

Now that the environment is configured, you can start the services using Docker Compose.

```bash
# In the root of the project directory
docker compose up --build -d
```

This command will:

- Build the scim-bridge Docker image from the source code.
- Pull the keycloak and mariadb images.
- Start all three containers in detached mode (`-d`).

Check the logs to ensure everything started correctly:

```bash
docker compose logs -f
# Press Ctrl+C to exit logs
```

---

### 5. Configure Keycloak

After the containers are running, you need to create a client in Keycloak for the Axiom bridge to use.

1. Navigate to your Keycloak instance: `https://keycloak.yourdomain.com`.
2. Log in with the admin credentials you set in the `.env` file.
3. Go to **Clients** in the **master** realm (or your target realm).
4. Click **Create client**.
5. Set the **Client ID** to match `KEYCLOAK_ADMIN_CLIENT_ID` from your `.env` file (e.g., `scim-bridge-client`).
6. Click **Next**.
7. Ensure **Client authentication** is **On** and select **Service accounts roles**.
8. Click **Save**.

A new set of tabs will appear. Go to the **Service account roles** tab.

- Click **Assign role**.
- Filter for **realm-management** and assign the following roles:
  - `manage-groups`
  - `manage-users`
  - `query-groups`
  - `query-users`
  - `view-users`

(For quick testing purpose, you can give the manage-realm role)

Go to the **Credentials** tab.

- You will see a **Client secret**.
- This secret must match the `KEYCLOAK_ADMIN_CLIENT_SECRET` in your `.env` file.
- If it doesn’t, copy it and update your `.env` file.
- Then restart the SCIM bridge container:

```bash
docker compose restart scim-bridge
```

---

### 🧪 Using the SCIM API

Your SCIM bridge is now ready to accept requests at `https://scim.yourdomain.com`.

To test your SCIM bridge endpoints directly, use one of the static tokens configured in src/main/resources/application.yml.
Replace YOUR_CONFIGURED_STATIC_TOKEN with an actual token value and scim.yourdomain.com with your SCIM bridge's public domain.

#### Get Service Provider Configuration:

```bash
STATIC_TOKEN="YOUR_CONFIGURED_STATIC_TOKEN"
curl -k -i "https://scim.yourdomain.com/scim/v2/ServiceProviderConfig" \
-H "Authorization: Bearer $STATIC_TOKEN"
```

#### Make API Calls

**Get all users:**

```bash
STATIC_TOKEN="YOUR_CONFIGURED_STATIC_TOKEN"
curl -k -i "https://scim.yourdomain.com/scim/v2/Users" \
-H "Authorization: Bearer $STATIC_TOKEN"
```

**Create a new user:**

```bash
STATIC_TOKEN="YOUR_CONFIGURED_STATIC_TOKEN"
NEW_SCIM_USER="curl-user-$(date +%s)"
curl -k -i -X POST "https://scim.yourdomain.com/scim/v2/Users" \
-H "Authorization: Bearer $STATIC_TOKEN" \
-H "Content-Type: application/scim+json" \
-d '{
  "schemas": ["urn:ietf:params:scim:schemas:core:2.0:User"],
  "userName": "'"$NEW_SCIM_USER"'",
  "name": {
    "givenName": "CurlTest",
    "familyName": "User"
  },
  "emails": [{"value": "'"$NEW_SCIM_USER"'@example.com", "primary": true}],
  "active": true
}'
```

---

## 🔌 Integrating with Identity Providers (e.g., Okta)

This bridge is built to the **SCIM 2.0** standard and has been successfully tested with **Okta** for automated user provisioning. The following steps outline how to configure Okta to provision users to your Keycloak instance via **Janus**.

1.  In your Okta Admin Console, navigate to **Applications** > **Applications** > **Browse App Catalog**.
2.  Search and select **SCIM 2.0 Test App (OAuth Bearer Token).
3.  Proceed to the **Provisioning** configuration screen.

#### Configuration Details

Fill in the SCIM connection form with the following details:

-   **SCIM connector base URL**: `https://scim.yourdomain.com/scim/v2`
    *(This is the `SCIM_BRIDGE_EXTERNAL_URL` from your .env file, with `/scim/v2` appended).*

-   **Authorization**: Provide a valid Bearer Token.
    *This is the static token from `application.yml`*

After saving, click **Test Connector Configuration** to ensure Okta can communicate with the Axiom bridge.

#### Provisioning Settings

Once the connection is established, navigate to the **Provisioning** tab for the application in Okta and click **Edit** under the **To App** settings.

Enable the following options:

-   **Create Users**
-   **Update User Attributes**
-   **Deactivate Users**

**IMPORTANT**: Do **not** enable the password synchronization option:

-   ❌ **Sync Password**

#### Final Steps

1.  **Assign Users/Groups**: In the **Assignments** tab, assign users or groups to the application. Once assigned, Okta will automatically start provisioning them to your Keycloak instance through the Axiom bridge.
2.  Verify the users and groups in Keycloak

---

