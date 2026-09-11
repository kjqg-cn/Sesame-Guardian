package io.github.lazyimmortal.sesamewatchdog.root;

public final class RootAccessVerifier {
    private RootAccessVerifier() {
    }

    public static Result verify() {
        RootCommandRunner.Result result = RootCommandRunner.verifyAccess();
        return new Result(result.isSuccess(), result.getOutput());
    }

    public static final class Result {
        private final boolean success;
        private final String message;

        private Result(boolean success, String message) {
            this.success = success;
            this.message = message;
        }

        public boolean isSuccess() {
            return success;
        }

        public String getMessage() {
            return message;
        }
    }
}
