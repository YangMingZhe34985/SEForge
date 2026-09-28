/** Local launcher preflight only; never packaged in the backend or production image. */
class DevHttpClientProbe {
    public static void main(String[] args) {
        // Constructing the client exercises the selector wakeup Pipe without any network request.
        try (var client = java.net.http.HttpClient.newHttpClient()) {
            System.out.println("HTTP_CLIENT_READY");
        }
    }
}
