/** Frontend form model. Adapt to AL-Core's actual API contract when agreed. */
export interface ValuationRequest {
  address: string;
  county: string;
  type: string;
  beds: number;
  baths: number;
  floor: number;
  built?: number;
  condition: string;
  energy?: string;
}
