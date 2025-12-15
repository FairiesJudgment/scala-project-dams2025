# Projet "Wish-list collaborative" (ex-WBocal)

## Ce que fait l'application

- Côté serveur (Scala + ZIO HTTP) :
  - Gère une liste de souhaits en mémoire (CRUD complet : création, lecture, modification, suppression).
  - Fournit un endpoint pour tirer un souhait au hasard (`/random`).
  - Diffuse en temps réel les nouveaux souhaits avec WebSocket (`/stream`).

- Côté client (fichier `Stream_site.html`) :
  - Permet d'ajouter un souhait (`title`, `details`, `priority`).
  - Affiche en temps réel les nouveaux souhaits reçus du serveur.
  - Contient une zone de test CRUD pour lister, lire, modifier ou supprimer des souhaits.

# Projet "Wish-list collaborative"

Ce dépôt contient une petite application serveur écrite en Scala + ZIO qui gère une liste de souhaits en mémoire et diffuse les nouveaux éléments via WebSocket.

Principales fonctionnalités
- API REST basique (CRUD) : `GET /wishes`, `GET /wish/:index`, `POST /wish`, `PUT /wish/:index`, `DELETE /wish/:index`.
- Endpoint pour tirer un souhait au hasard : `GET /random` (retourne 404 si la liste est vide).
- WebSocket de diffusion des nouveaux wishes : `GET /stream` (messages JSON).
- Client d'exemple : `Stream_site.html` (interface minimale pour tester le stream et le POST).

Prerequis
- Java 17
- sbt (local) — CI installe sbt via `coursier` so runners don't need sbt preinstalled.

Lancer le serveur
1. Depuis la racine du projet (même dossier que `build.sbt`) :

```bash
sbt run
```

2. Par défaut le serveur écoute sur `http://127.0.0.1:8080`.
   Pour changer le port :

```bash
sbt -Dhttp.port=8081 run
```

Notes : le serveur lit une propriété système `http.port` si fournie; nous utilisons `Server.default` pour la configuration.

Tester rapidement l'API (exemples)

- Lister tous les wishes :

```bash
curl http://127.0.0.1:8080/wishes
```

- Créer un wish (retourne `201` + body créé) :

```bash
curl -X POST http://127.0.0.1:8080/wish \
  -H 'Content-Type: application/json' \
  -d '{"title":"Offrir une plante","details":"Plante verte","priority":2}'
```

- Récupérer un wish par index :

```bash
curl http://127.0.0.1:8080/wish/0
```

- Tirer un wish aléatoire :

```bash
curl http://127.0.0.1:8080/random
```

Client HTML
1. Ouvrez `Stream_site.html` dans votre navigateur (double-click ou `File -> Open`).
2. Assurez-vous que le serveur tourne sur `http://127.0.0.1:8080`.
3. Le client se connecte à `ws://127.0.0.1:8080/stream` pour recevoir les nouveaux wishes et propose un formulaire pour en poster.

Tests
- Lancer tous les tests :

```bash
sbt test
```

- Lancer seulement l'intégration / E2E :

```bash
sbt "testOnly *IntegrationSpec"
sbt "testOnly *WebSocketE2ESpec"
```

Formatting & CI
- `scalafmt` est appliqué en CI (`sbt scalafmtCheckAll`). Si CI échoue sur le style, lancez `sbt scalafmtAll` localement et poussez le résultat.
- CI installe sbt via `coursier` and runs `sbt test`. Les runs utilisent caching (coursier/ivy) pour accélérer.

Notes techniques & limites
- Stockage en mémoire (`Ref[Vector[Wish]]`) — pas de persistance.
- WebSocket broadcast implemented with `Hub[Wish]`.
- Tests peuvent être sensibles au timing (le WebSocket E2E a été durci pour la CI).

Besoin d'aide pour tester manuellement ?
- Dis-moi ton OS et navigateur et je te fournis une check-list pas-à-pas (ou des commandes PowerShell/Bash pour automatiser une session de test).
