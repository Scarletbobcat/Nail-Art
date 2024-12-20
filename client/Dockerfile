# FROM node:23

# WORKDIR /src

# COPY . .

# RUN npm install

# EXPOSE 5173

# CMD ["npm", "run", "dev"]


FROM node:23 AS build

WORKDIR /app

COPY package.json .

RUN npm install

COPY . .

RUN npm run build

FROM node:23-alpine AS production

WORKDIR /app

COPY --from=build /app/dist ./dist

RUN npm i -g serve

EXPOSE 5173

CMD [ "serve", "-s", "dist", "-l", "5173" ] 