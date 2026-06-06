FROM node:20-alpine AS build
WORKDIR /app
COPY package*.json ./
RUN npm install
COPY nest-cli.json tsconfig.json ./
COPY src ./src
RUN npm run build

FROM node:20-alpine
WORKDIR /app
COPY package*.json ./
RUN npm install --omit=dev
COPY --from=build /app/dist ./dist
EXPOSE 3002
CMD ["npm", "start"]
