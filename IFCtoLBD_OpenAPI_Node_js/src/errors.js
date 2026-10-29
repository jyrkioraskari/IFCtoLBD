export class ApiError extends Error {
  constructor(statusCode, code, message) {
    super(message);
    this.name = "ApiError";
    this.statusCode = statusCode;
    this.code = code;
  }
}

export class QueueFullError extends ApiError {
  constructor() {
    super(503, "CONVERSION_QUEUE_FULL", "The conversion queue is full. Try again later.");
  }
}
