import unittest
from pydantic import ValidationError

from app import Features, health, predict


class FraudPredictionTests(unittest.TestCase):
    def test_saved_model_is_loaded(self):
        self.assertTrue(health()["modelLoaded"])

    def test_high_risk_features_return_a_bounded_score_and_reasons(self):
        result = predict(Features(amount=250000, channel="IMPS", hour=2, frequency_1h=6, new_payee=1))
        self.assertGreaterEqual(result["score"], 0.0)
        self.assertLessEqual(result["score"], 1.0)
        self.assertIn("high_amount", result["reasons"])
        self.assertIn("off_hours", result["reasons"])
        self.assertIn("velocity", result["reasons"])

    def test_rejects_invalid_feature_ranges(self):
        with self.assertRaises(ValidationError):
            Features(amount=-1)
        with self.assertRaises(ValidationError):
            Features(amount=25, hour=24)
        with self.assertRaises(ValidationError):
            Features(amount=25, new_payee=2)


if __name__ == "__main__":
    unittest.main()
