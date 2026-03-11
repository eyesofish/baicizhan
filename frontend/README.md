# Baicizhan Frontend (MVP)

## Run

```bash
npm install
npm run dev
```

Default dev URL: `http://localhost:5173`  
Default backend URL: `http://localhost:8080`

You can override backend URL by:
- setting `VITE_API_BASE_URL` in `.env`
- or updating it in the page header input at runtime

## Build

```bash
npm run build
```

## Implemented pages

- Auth: register/login (JWT)
- Vocab lists: create list, query my lists, add item into list
- Term detail: query by `termId`
- Review flow: fetch next review queue, submit rating (0-5)
- AI jobs: create enrich job, query job status
