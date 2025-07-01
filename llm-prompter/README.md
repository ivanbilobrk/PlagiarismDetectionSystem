# Upute

Install conda environment:

```bash
conda env create --file=environment.yaml
```

After the environment is created, download the `.env` file and place it in the `llm-prompter` directory.

### Application start:

```bash
python -m uvicorn src.main:app
```

### Application development start:

```bash
python -m uvicorn src.main:app --reload
```
