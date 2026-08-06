# Credence AI assistant setup

The assistant stores conversation history per authenticated manager in the Manager database.

## Add your Cline API key

Never add the key to Angular, application.yml, or Git. Set it in the terminal that starts Manager:

```powershell
$env:CLINE_API_KEY = "paste-your-cline-api-key-here"
$env:CLINE_MODEL = "minimax/minimax-m2.5"
mvnw.cmd spring-boot:run
```

CLINE_MODEL must be available to your Cline API key. The example uses a documented free model; replace it with your chosen Cline model if needed.

Optional: CLINE_API_URL defaults to https://api.cline.bot/api/v1/chat/completions.
Without CLINE_API_KEY, history still works and the chat displays a configuration reminder.