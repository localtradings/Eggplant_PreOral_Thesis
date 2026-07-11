export const REQUEST_REVIEW_STATUSES = ["under_review", "planned", "needs_information", "not_supported", "closed"] as const;

export type RequestReviewStatus = (typeof REQUEST_REVIEW_STATUSES)[number];
