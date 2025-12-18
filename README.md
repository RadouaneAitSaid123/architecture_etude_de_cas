# Rapport d'Étude de Cas : Microservices, Performance et Résilience

## 1. Contexte et Objectifs
Ce laboratoire a pour objectif de mettre en place une architecture microservices avec Spring Boot 3 et d'analyser comparativement trois méthodes de communication inter-services (**RestTemplate**, **OpenFeign**, **WebClient**) sous deux modes de découverte de services (**Eureka** et **Consul**).

L'étude se concentre sur trois axes :
1.  **Performance** (Latence et Débit).
2.  **Consommation de ressources** (CPU/RAM).
3.  **Résilience** (Comportement face aux pannes).

---

## 2. Architecture Technique

### Composants
* **Service Voiture** (Fournisseur) : Expose une API REST avec une latence simulée de **50ms** (`Thread.sleep(50)`).
* **Service Client** (Consommateur) : Interroge le service voiture via 3 implémentations (Rest, Feign, WebClient).
* **Discovery Service** : Testé successivement avec **Netflix Eureka** puis **HashiCorp Consul**.

### Protocole de Test (JMeter)
* **Utilisateurs simultanés** : 50 (Stabilisé pour éviter la saturation OS).
* **Régulation** : Utilisation d'un *Constant Timer* pour lisser le débit.
* **Environnement** : Exécution locale (Windows), Java 17.

---

## 3. Analyse des Performances (Latence & Débit)

Les tests ont été calibrés pour délivrer un débit stable afin de comparer la latence et la stabilité à charge égale.

### Tableau 1 : Résultats sous Eureka

| Client HTTP | Moyenne (ms) | Max (ms) | Débit (req/s) | % Erreur |
| :--- | :---: | :---: | :---: | :---: |
| **RestTemplate** | 65 | 89 | 264 | 0% |
| **OpenFeign** | 65 | 96 | 264 | 0% |
| **WebClient** | 66 | 88 | 264 | 0% |

> **Observation :** Les performances sont identiques pour les trois clients. La latence moyenne (65ms) est cohérente avec le délai artificiel de 50ms + le temps de transport réseau local (~15ms).

### Tableau 2 : Résultats sous Consul (Migration)

| Client HTTP | Moyenne (ms) | Max (ms) | Débit (req/s) |
| :--- | :---: | :---: | :---: |
| **RestTemplate** | 65 | 321 | 265 |
| **OpenFeign** | 66 | 239 | 264 |
| **WebClient** | 66 | 897 | 263 |

> **Observation :** La migration vers Consul n'a **aucun impact négatif** sur la moyenne (toujours ~65ms). Nous observons quelques pics de latence ("Max") plus élevés sous Consul, probablement dus à des micro-freeze du système (Garbage Collection ou surcharge CPU ponctuelle) lors de cette deuxième phase de test, plutôt qu'au protocole lui-même.

---

## 4. Consommation des Ressources (CPU / Mémoire)

Mesures prises via *JConsole* / *Task Manager* durant la charge stable (264 req/s).

| Client | CPU Usage (Approx.) | Analyse |
| :--- | :---: | :--- |
| **RestTemplate** | **44%** | Le plus léger. C'est un client bas niveau sans surcouche complexe. |
| **WebClient** | **50%** | Usage modéré. En mode bloquant (`.block()`), il ne tire pas profit de son architecture non-bloquante mais garde un overhead raisonnable. |
| **OpenFeign** | **51%** | Le plus consommateur (+7% vs Rest). Cela s'explique par la création dynamique de Proxies au runtime (réflexion Java). |

---

## 5. Tests de Résilience et Tolérance aux Pannes

### Scénario F1 : Panne du Service Voiture
*Action : Arrêt brutal du fournisseur pendant le test de charge.*

* **Résultat Eureka :** Reprise du trafic (vert) après **12 secondes**.
* **Résultat Consul :** Reprise du trafic après **11 secondes**.
* **Analyse :** Le système est à "cohérence éventuelle". Ce délai correspond au temps nécessaire pour que :
    1.  Le service redémarre.
    2.  Le Discovery Server reçoive le signal de vie (Heartbeat/HealthCheck).
    3.  Le Service Client rafraîchisse son cache local (Client-side load balancing).

### Scénario F2 : Panne du Discovery Server
*Action : Arrêt d'Eureka/Consul pendant que les services tournent.*

* **Résultat :** **Aucune interruption de service.** Les requêtes continuent de passer à 100%.
* **Explication :** Les clients (Feign/Rest/WebClient) possèdent un **cache local** des adresses. Tant que les IPs des services ne changent pas, la mort du Discovery Server n'impacte pas le trafic existant. Le Discovery Server n'est pas un *Single Point of Failure* (SPOF) immédiat.

---

## 6. Difficultés Rencontrées et Solutions

Lors de la première phase de test (100 utilisateurs), nous avons rencontré un taux d'erreur de **78%** avec l'exception :
`java.net.BindException: Address already in use: connect`

* **Cause :** Épuisement des ports éphémères Windows (Port Exhaustion). JMeter ouvrait et fermait trop de connexions TCP, laissant des milliers de ports en état `TIME_WAIT`.
* **Solution appliquée :**
    1.  Activation du **KeepAlive** dans JMeter (réutilisation des connexions TCP).
    2.  Configuration d'un **Timeout Feign** (5s) pour éviter les coupures prématurées.
    3.  Stabilisation de la charge à **50 utilisateurs** avec un *Timer* fixe.
    *Résultat final : 0.22% d'erreur (négligeable).*

---

## 7. Conclusion

Cette étude permet de tirer les conclusions suivantes pour une architecture microservices Java :

1.  **Choix du Client HTTP :**
    * **Feign** est recommandé pour la **maintenabilité**. Malgré une légère surconsommation CPU (+7%), la clarté du code (Interface vs Implémentation) est un atout majeur.
    * **RestTemplate** reste performant mais verbeux.
    * **WebClient** est pertinent uniquement si toute la stack est réactive (WebFlux), sinon il n'apporte pas de gain en mode synchrone.

2.  **Eureka vs Consul :**
    * Les deux solutions offrent des performances similaires en termes de résolution de nom.
    * **Consul** est préféré pour des environnements de production modernes (hors écosystème Netflix pur) grâce à son mécanisme de *Health Check* actif plus robuste.

3.  **Résilience :** L'architecture est robuste face à la perte du serveur de découverte grâce au cache client, mais présente une latence de ~10s lors du redémarrage d'une instance de service.
